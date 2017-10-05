package stroom.annotations.service.resources;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import io.dropwizard.testing.ConfigOverride;
import io.dropwizard.testing.junit.DropwizardAppRule;
import org.apache.http.HttpStatus;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import stroom.annotations.service.App;
import stroom.annotations.service.model.AnnotationDTO;
import stroom.annotations.service.model.AnnotationHistoryDTO;
import stroom.annotations.service.model.HistoryOperation;
import stroom.annotations.service.model.Status;

import java.io.IOException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class AnnotationsResourcesIT {

    @ClassRule
    public static final DropwizardAppRule appRule = new DropwizardAppRule(App.class, "config.yml", new ConfigOverride[0]);

    private static String annotationsUrl;

    private static final com.fasterxml.jackson.databind.ObjectMapper jacksonObjectMapper =
            new com.fasterxml.jackson.databind.ObjectMapper();

    private static String getAnnotationUrl(final String id) {
        return String.format("%s/single/%s", annotationsUrl, id);
    }

    private static String getHistoryUrl(final String id) {
        return String.format("%s/single/%s/history", annotationsUrl, id);
    }

    private static String getSearchUrl() {
        return String.format("%s/search", annotationsUrl);
    }

    @BeforeClass
    public static void setupClass() {
        int appPort = appRule.getLocalPort();
        annotationsUrl = "http://localhost:" + appPort + "/annotations/v1";

        Unirest.setObjectMapper(new com.mashape.unirest.http.ObjectMapper() {
            private com.fasterxml.jackson.databind.ObjectMapper jacksonObjectMapper
                    = new com.fasterxml.jackson.databind.ObjectMapper();

            public <T> T readValue(String value, Class<T> valueType) {
                try {
                    return jacksonObjectMapper.readValue(value, valueType);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }

            public String writeValue(Object value) {
                try {
                    return jacksonObjectMapper.writeValueAsString(value);
                } catch (JsonProcessingException e) {
                    throw new RuntimeException(e);
                }
            }
        });
    }

    @Test
    public void testWelcome() throws UnirestException {
        final HttpResponse<String> response = Unirest
                .get(annotationsUrl + "/static/welcome")
                .asString();

        assertEquals(AnnotationsResource.WELCOME_TEXT, response.getBody());
    }

    @Test
    public void testStatusValues() throws UnirestException, IOException {
        final HttpResponse<String> response = Unirest
                .get(annotationsUrl + "/static/statusValues")
                .asString();

        final Map<String, String> responseStatusValues = jacksonObjectMapper.readValue(response.getBody(), new TypeReference<Map<String, String>>(){});
        final Map<String, String> statusValues = Arrays.stream(Status.values())
                .collect(Collectors.toMap(Object::toString, Status::getDisplayText));

        assertEquals(HttpStatus.SC_OK, response.getStatus());
        assertEquals(statusValues, responseStatusValues);
    }

    @Test
    public void testCreateAnnotation() throws UnirestException, IOException {
        final String id = UUID.randomUUID().toString();
        createAnnotation(id);
    }

    @Test
    public void testCreateUpdateAndGetAnnotation() {
        // Create some test data
        final Map<String, AnnotationDTO> annotations = IntStream.range(0, 10)
                .mapToObj(i -> UUID.randomUUID().toString())
                .map(uuid -> new AnnotationDTO.Builder().id(uuid)
                        .content(UUID.randomUUID().toString())
                        .assignTo(UUID.randomUUID().toString())
                        .status(Status.OPEN_ESCALATED)
                        .build())
                .peek(this::createAnnotation) // add to database
                .peek(this::updateAnnotation) // update with initial state
                .collect(Collectors.toMap(AnnotationDTO::getId, Function.identity()));

        // Try and fetch each annotation
        annotations.forEach((id, annotation) -> {
            final AnnotationDTO annotationResponse = getAnnotation(id);
            assertEquals(annotation.getContent(), annotationResponse.getContent());
        });
    }

    @Test
    public void testDelete() throws UnirestException {
        final String id = UUID.randomUUID().toString();
        createAnnotation(id);
        getAnnotation(id);
        deleteAnnotation(id);
        HttpResponse<String> result = Unirest
                .get(getAnnotationUrl(id))
                .asString();
        assertEquals(HttpStatus.SC_NOT_FOUND, result.getStatus());
    }

    @Test
    public void testGetHistory() {
        final String id = UUID.randomUUID().toString();
        final int numberUpdates = 5;
        createAnnotation(id); // create initial empty annotation

        final List<AnnotationDTO> annotationHistory =
                IntStream.range(0, numberUpdates).mapToObj(i -> new AnnotationDTO.Builder()
                        .id(id)
                        .content(UUID.randomUUID().toString())
                        .assignTo(UUID.randomUUID().toString())
                        .status(Status.OPEN_ESCALATED)
                        .build())
                        .peek(this::updateAnnotation) // push update to database
                        .collect(Collectors.toList());

        deleteAnnotation(id);

        final List<AnnotationHistoryDTO> result = getHistory(id);
        assertEquals(numberUpdates + 2, result.size());

        assertEquals(HistoryOperation.CREATE, result.get(0).getOperation());
        IntStream.range(0, annotationHistory.size()).forEach(index -> {
            assertEquals(HistoryOperation.UPDATE, result.get(index + 1).getOperation());
            assertEquals(annotationHistory.get(index), result.get(index + 1).getAnnotation());
        });
        assertEquals(HistoryOperation.DELETE, result.get(6).getOperation());
    }

    @Test
    public void testSearch() {
        // Generate an UUID we can embed into the content of some annotations so we can find them
        final List<String> searchTerms = IntStream.range(0, 10)
                .mapToObj(i -> UUID.randomUUID().toString())
                .collect(Collectors.toList());

        // Create some test data for each search term
        final Map<String, Set<AnnotationDTO>> annotationsBySearchTerm = new HashMap<>();
        for (final String searchTerm : searchTerms) {
            final Set<AnnotationDTO> annotations = IntStream.range(0, 10)
                    .mapToObj(i -> UUID.randomUUID().toString()) // Generate an ID
                    .map(uuid -> new AnnotationDTO.Builder().id(uuid)
                            .content(UUID.randomUUID().toString() + searchTerm)
                            .assignTo(UUID.randomUUID().toString())
                            .status(Status.OPEN_ESCALATED)
                            .build())
                    .peek(this::createAnnotation) // add to database
                    .peek(this::updateAnnotation) // update with initial state
                    .collect(Collectors.toSet());
            annotationsBySearchTerm.put(searchTerm, annotations);
        }

        annotationsBySearchTerm.forEach((searchTerm, annotationsSet) -> {
            final List<AnnotationDTO> resultsList = searchAnnotation(searchTerm);
            final Set<AnnotationDTO> resultsSet = new HashSet<>(resultsList);

            assertEquals(annotationsSet, resultsSet);
        });
    }

    /**
     * Various utility functions for making calls to the REST API
     */

    /**
     * Given an annotation DTO, creates an annotation for the ID. It makes no use of the
     * other property values in the annotation, a further update call would be required to set the other values.
     * @param annotation The annotation to create
     * @return The initial state of the annotation
     */
    private AnnotationDTO createAnnotation(final AnnotationDTO annotation) {
        return createAnnotation(annotation.getId());
    }

    /**
     * Creates an annotation for the ID given.
     * @param id The ID to annotate
     * @return The initial state of the annotation
     */
    private AnnotationDTO createAnnotation(final String id) {
        AnnotationDTO result = null;
        try {
            HttpResponse<String> response = Unirest.post(getAnnotationUrl(id))
                    .asString();
            assertEquals(HttpStatus.SC_OK, response.getStatus());

            result = jacksonObjectMapper.readValue(response.getBody(), AnnotationDTO.class);
            assertEquals(id, result.getId());
            assertEquals(AnnotationDTO.DEFAULT_CONTENT, result.getContent());
            assertEquals(AnnotationDTO.DEFAULT_ASSIGNEE, result.getAssignTo());
            assertEquals(AnnotationDTO.DEFAULT_UPDATED_BY, result.getUpdatedBy());
            assertEquals(AnnotationDTO.DEFAULT_STATUS, result.getStatus());
        } catch (Exception e) {
            fail(e.getLocalizedMessage());
        }
        return result;
    }

    /**
     * Updates the annotation by PUTting the new annotation data.
     * @param annotation The annotation to update
     * @return The updated annotation returned from the service.
     */
    private AnnotationDTO updateAnnotation(final AnnotationDTO annotation) {
        AnnotationDTO result = null;

        try {
            // Set the content in an update
            HttpResponse<String> response = Unirest
                    .put(getAnnotationUrl(annotation.getId()))
                    .header("accept", "application/json")
                    .header("Content-Type", "application/json")
                    .body(annotation)
                    .asString();
            assertEquals(HttpStatus.SC_OK, response.getStatus());

            result = jacksonObjectMapper.readValue(response.getBody(), AnnotationDTO.class);
            assertEquals(annotation, result);
        } catch (Exception e) {
            fail(e.getLocalizedMessage());
        }
        return result;
    }

    /**
     * Searches for annotations that contain the queryTerm given
     * @param queryTerm The term to search for
     * @return The list of annotations found by the service.
     */
    private List<AnnotationDTO> searchAnnotation(final String queryTerm) {
        List<AnnotationDTO> result = null;

        try {
            final HttpResponse<String> response = Unirest
                    .get(getSearchUrl())
                    .queryString("q", queryTerm)
                    .asString();

            assertEquals(HttpStatus.SC_OK, response.getStatus());

            result = jacksonObjectMapper.readValue(response.getBody(), new TypeReference<List<AnnotationDTO>>(){});
        } catch (Exception e) {
            fail(e.getLocalizedMessage());
        }

        return result;
    }

    /**
     * Given an ID, calls GET on the service to retrieve that specific annotation.
     * This function asserts that the annotation was found correctly.
     *
     * @param id The ID to find.
     * @return The current annotation for that ID from the service.
     */
    private AnnotationDTO getAnnotation(final String id) {
        AnnotationDTO result = null;

        try {
            final HttpResponse<String> response = Unirest
                    .get(getAnnotationUrl(id))
                    .asString();

            assertEquals(HttpStatus.SC_OK, response.getStatus());

            result = jacksonObjectMapper.readValue(response.getBody(), AnnotationDTO.class);

            assertEquals(id, result.getId());
        } catch (Exception e) {
            fail(e.getLocalizedMessage());
        }

        return result;
    }

    /**
     * Given an ID, attempts to delete the annotation
     * @param id The ID to delete
     */
    private void deleteAnnotation(final String id) {
        try {
            final HttpResponse<String> response = Unirest.delete(getAnnotationUrl(id)).asString();
            assertEquals(HttpStatus.SC_OK, response.getStatus());
        } catch (UnirestException e) {
            fail(e.getLocalizedMessage());
        }
    }

    /**
     * Retrieves the annotations history for a particular ID. This can be called for ID's that have been deleted.
     * @param id The ID to find the history for
     * @return The list of history items.
     */
    private List<AnnotationHistoryDTO> getHistory(final String id) {
        List<AnnotationHistoryDTO> result = null;

        try {
            final HttpResponse<String> response = Unirest
                    .get(getHistoryUrl(id))
                    .asString();

            assertEquals(HttpStatus.SC_OK, response.getStatus());

            result = jacksonObjectMapper.readValue(response.getBody(), new TypeReference<List<AnnotationHistoryDTO>>() {});
        } catch (Exception e) {
            fail(e.getLocalizedMessage());
        }

        return result;
    }
}
