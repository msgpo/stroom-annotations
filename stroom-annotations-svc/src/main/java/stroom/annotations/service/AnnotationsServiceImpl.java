package stroom.annotations.service;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.annotations.hibernate.Annotation;
import stroom.annotations.hibernate.AnnotationHistory;
import stroom.annotations.hibernate.HistoryOperation;
import stroom.query.audit.security.ServiceUser;
import stroom.query.hibernate.QueryableEntity;
import stroom.util.shared.QueryApiException;

import javax.inject.Inject;
import javax.persistence.NoResultException;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaDelete;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.CriteriaUpdate;
import javax.persistence.criteria.Root;
import java.util.List;
import java.util.Optional;

public class AnnotationsServiceImpl implements AnnotationsService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AnnotationsServiceImpl.class);

    private SessionFactory database;

    public static final int SEARCH_PAGE_LIMIT = 10;

    @Inject
    public AnnotationsServiceImpl(final SessionFactory database) {
        this.database = database;
    }

    @Override
    public List<Annotation> search(final ServiceUser authenticatedServiceUser,
                                   final String index,
                                   final String q,
                                   final Integer seekPosition) throws QueryApiException {
        try (final Session session = database.openSession()){
            LOGGER.info(String.format("Searching the annotations for %s, pagination information (position=%d)",
                    q, seekPosition));

            final CriteriaBuilder cb = session.getCriteriaBuilder();

            final CriteriaQuery<Annotation> cq = cb.createQuery(Annotation.class);
            final Root<Annotation> root = cq.from(Annotation.class);

            final String likeTerm = String.format("%%%s%%", q);

            cq.where(
                    cb.and(
                            cb.equal(root.get(QueryableEntity.DATA_SOURCE_UUID), index),
                            cb.or(
                                    cb.like(root.get(Annotation.ID), likeTerm),
                                    cb.like(root.get(Annotation.CONTENT), likeTerm),
                                    cb.like(root.get(Annotation.ASSIGN_TO), likeTerm)
                            )
                    )
            );

            cq.orderBy(cb.desc(root.get(Annotation.ID)));

            return session.createQuery(cq)
                    .setMaxResults(SEARCH_PAGE_LIMIT)
                    .setFirstResult((seekPosition != null) ? seekPosition : 0)
                    .getResultList();

        } catch (final Exception e) {
            LOGGER.warn("Failed to search for annotations", e);
            throw new QueryApiException(e);
        }
    }

    @Override
    public Optional<Annotation> get(final ServiceUser authenticatedServiceUser,
                                    final String index,
                                    final String id) throws QueryApiException {
        try (final Session session = database.openSession()) {
            return Optional.of(getEntity(session, index, id));
        } catch (NoResultException e) {
            return Optional.empty();
        } catch (final Exception e) {
            LOGGER.warn("Failed to get history of annotation", e);
            throw new QueryApiException(e);
        }
    }

    @Override
    public Optional<List<AnnotationHistory>> getHistory(final ServiceUser authenticatedServiceUser,
                                                        final String index,
                                                        final String id) throws QueryApiException {
        try (final Session session = database.openSession()){
            final CriteriaBuilder cb = session.getCriteriaBuilder();
            final CriteriaQuery<AnnotationHistory> cq = cb.createQuery(AnnotationHistory.class);
            final Root<AnnotationHistory> root = cq.from(AnnotationHistory.class);

            cq.where(cb.and(
                    cb.equal(root.get(AnnotationHistory.ANNOTATION_ID), id),
                    cb.equal(root.get(QueryableEntity.DATA_SOURCE_UUID), index)
            ));

            return Optional.of(session.createQuery(cq).getResultList());
        } catch (NoResultException e) {
            return Optional.empty();
        } catch (final Exception e) {
            LOGGER.warn("Failed to get history of annotation", e);
            throw new QueryApiException(e);
        }
    }

    @Override
    public Optional<Annotation> create(final ServiceUser authenticatedServiceUser,
                                       final String index,
                                       final String id) throws QueryApiException {
        Transaction tx = null;

        try (final Session session = database.openSession()) {
            tx = session.beginTransaction();

            final Annotation annotation = new Annotation.Builder()
                    .id(id)
                    .dataSourceUuid(index)
                    .lastUpdated(System.currentTimeMillis())
                    .assignTo(Annotation.DEFAULT_ASSIGNEE)
                    .updatedBy(Annotation.DEFAULT_UPDATED_BY)
                    .content(Annotation.DEFAULT_CONTENT)
                    .status(Annotation.DEFAULT_STATUS)
                    .build();
            session.persist(annotation);

            final Annotation currentState = updateHistoryAndReturn(session, index, id, HistoryOperation.CREATE);

            tx.commit();

            return Optional.of(currentState);

        } catch (NoResultException e) {
            return Optional.empty();
        } catch (final Exception e) {
            if (tx!=null) tx.rollback();
            LOGGER.warn("Failed to get create annotation", e);

            throw new QueryApiException(e);
        }
    }

    @Override
    public Optional<Annotation> update(final ServiceUser authenticatedServiceUser,
                                       final String index,
                                       final String id,
                                       final Annotation annotationUpdate) throws QueryApiException {
        Transaction tx = null;

        try (final Session session = database.openSession()) {
            tx = session.beginTransaction();

            final CriteriaBuilder cb = session.getCriteriaBuilder();
            final CriteriaUpdate<Annotation> cq = cb.createCriteriaUpdate(Annotation.class);
            final Root<Annotation> root = cq.from(Annotation.class);

            cq.set(root.get(Annotation.LAST_UPDATED), System.currentTimeMillis());
            cq.set(root.get(Annotation.ASSIGN_TO), annotationUpdate.getAssignTo());
            cq.set(root.get(Annotation.CONTENT), annotationUpdate.getContent());
            cq.set(root.get(Annotation.STATUS), annotationUpdate.getStatus());

            cq.where(
                    cb.and(
                            cb.equal(root.get(QueryableEntity.DATA_SOURCE_UUID), index),
                            cb.equal(root.get(Annotation.ID), id)
                    )
            );

            int rowsAffected = session.createQuery(cq).executeUpdate();

            if (rowsAffected == 0) {
                throw new Exception("Zero rows affected by the update");
            }

            final Annotation currentState = updateHistoryAndReturn(session, index, id, HistoryOperation.UPDATE);

            tx.commit();

            return Optional.of(currentState);

        } catch (NoResultException e) {
            return Optional.empty();
        } catch (final Exception e) {
            if (tx!=null) tx.rollback();
            LOGGER.warn("Failed to get update annotation", e);
            throw new QueryApiException(e);
        }
    }

    @Override
    public Optional<Boolean> remove(final ServiceUser authenticatedServiceUser,
                                    final String index,
                                    final String id) throws QueryApiException {
        Transaction tx = null;

        try (final Session session = database.openSession()) {

            // Take the history snapshot before deletion happens
            takeAnnotationHistoryDelete(session, index, id);

            tx = session.beginTransaction();

            final CriteriaBuilder cb = session.getCriteriaBuilder();

            final CriteriaDelete<Annotation> cq = cb.createCriteriaDelete(Annotation.class);
            final Root<Annotation> root = cq.from(Annotation.class);

            cq.where(
                    cb.and(
                            cb.equal(root.get(QueryableEntity.DATA_SOURCE_UUID), index),
                            cb.equal(root.get(Annotation.ID), id)
                    )
            );

            int rowsAffected = session.createQuery(cq).executeUpdate();
            if (rowsAffected == 0) {
                throw new Exception("Zero rows affected by the update");
            }

            tx.commit();

            return Optional.of(Boolean.TRUE);
        } catch (NoResultException e) {
            return Optional.empty();
        } catch (final Exception e) {
            if (tx!=null) tx.rollback();
            LOGGER.warn("Failed to get create annotation", e);
            throw new QueryApiException(e);
        }
    }

    private Annotation getEntity(final Session session, final String index, final String id) {
        final CriteriaBuilder cb = session.getCriteriaBuilder();

        final CriteriaQuery<Annotation> cq = cb.createQuery(Annotation.class);
        final Root<Annotation> root = cq.from(Annotation.class);
        cq.where(cb.and(
                cb.equal(root.get(Annotation.ID), id),
                cb.equal(root.get(QueryableEntity.DATA_SOURCE_UUID), index)
        ));

        return session.createQuery(cq).getSingleResult();
    }

    private Annotation updateHistoryAndReturn(final Session session,
                                        final String index,
                                        final String id,
                                        final HistoryOperation operation) {
        final Annotation currentState = getEntity(session, index, id);

        final AnnotationHistory history = new AnnotationHistory.Builder()
                .dataSourceUuid(currentState.getDataSourceUuid())
                .annotationId(currentState.getId())
                .operation(operation)
                .lastUpdated(currentState.getLastUpdated())
                .assignTo(currentState.getAssignTo())
                .updatedBy(currentState.getUpdatedBy())
                .content(currentState.getContent())
                .status(currentState.getStatus())
                .build();
        session.persist(history);

        LOGGER.trace(String.format("History Point Taken for Annotation %s", currentState.getId()));

        return currentState;
    }

    private void takeAnnotationHistoryDelete(final Session session,
                                             final String index,
                                             final String id) {
        final Annotation currentState = getEntity(session, index, id);

        final AnnotationHistory history = new AnnotationHistory.Builder()
                .dataSourceUuid(index)
                .annotationId(id)
                .operation(HistoryOperation.DELETE)
                .lastUpdated(System.currentTimeMillis())
                .assignTo(currentState.getAssignTo())
                .updatedBy(Annotation.DEFAULT_UPDATED_BY)
                .content(currentState.getContent())
                .status(currentState.getStatus())
                .build();
        session.persist(history);

        LOGGER.trace(String.format("History Point Taken for Annotation %s", id));
    }
}
