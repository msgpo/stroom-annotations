package stroom.annotations.service.hibernate;

import stroom.annotations.service.model.Status;
import stroom.datasource.api.v2.DataSourceField;
import stroom.query.api.v2.ExpressionTerm;
import stroom.query.hibernate.IsDataSourceField;
import stroom.query.hibernate.QueryableEntity;

import javax.persistence.*;
import java.util.Arrays;
import java.util.function.Supplier;

@Entity(name="annotations")
public class Annotation extends QueryableEntity {
    public static final String ID = "id";
    public static final String STATUS = "status";
    public static final String ASSIGN_TO = "assignTo";
    public static final String LAST_UPDATED = "lastUpdated";
    public static final String CONTENT = "content";
    public static final String UPDATED_BY = "updatedBy";

    private String id;

    private String assignTo;

    private Long lastUpdated;

    private String updatedBy;

    private Status status;

    private String content;

    public static class IdField implements Supplier<DataSourceField> {

        @Override
        public DataSourceField get() {
            return new DataSourceField(
                    DataSourceField.DataSourceFieldType.ID,
                    Annotation.ID,
                    true,
                    Arrays.asList(
                            ExpressionTerm.Condition.EQUALS,
                            ExpressionTerm.Condition.IN,
                            ExpressionTerm.Condition.IN_DICTIONARY
                    )
            );
        }
    }

    @Id
    @Column(name=ID)
    @IsDataSourceField(fieldSupplier = IdField.class)
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public static class StatusField implements Supplier<DataSourceField> {

        @Override
        public DataSourceField get() {
            return new DataSourceField(
                    DataSourceField.DataSourceFieldType.FIELD,
                    Annotation.STATUS,
                    true,
                    Arrays.asList(
                            ExpressionTerm.Condition.EQUALS,
                            ExpressionTerm.Condition.IN,
                            ExpressionTerm.Condition.IN_DICTIONARY
                    )
            );
        }
    }

    @Enumerated(EnumType.STRING)
    @Column(name=STATUS)
    @IsDataSourceField(fieldSupplier = StatusField.class)
    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public static class AssignToField implements Supplier<DataSourceField> {

        @Override
        public DataSourceField get() {
            return new DataSourceField(
                    DataSourceField.DataSourceFieldType.FIELD,
                    Annotation.ASSIGN_TO,
                    true,
                    Arrays.asList(
                            ExpressionTerm.Condition.EQUALS,
                            ExpressionTerm.Condition.CONTAINS,
                            ExpressionTerm.Condition.IN,
                            ExpressionTerm.Condition.IN_DICTIONARY
                    )
            );
        }
    }

    @Column(name=ASSIGN_TO)
    @IsDataSourceField(fieldSupplier = AssignToField.class)
    public String getAssignTo() {
        return assignTo;
    }

    public void setAssignTo(String assignTo) {
        this.assignTo = assignTo;
    }

    public static class LastUpdatedField implements Supplier<DataSourceField> {

        @Override
        public DataSourceField get() {
            return new DataSourceField(
                    DataSourceField.DataSourceFieldType.DATE_FIELD,
                    Annotation.LAST_UPDATED,
                    true,
                    Arrays.asList(
                            ExpressionTerm.Condition.BETWEEN,
                            ExpressionTerm.Condition.EQUALS,
                            ExpressionTerm.Condition.GREATER_THAN,
                            ExpressionTerm.Condition.GREATER_THAN_OR_EQUAL_TO,
                            ExpressionTerm.Condition.LESS_THAN,
                            ExpressionTerm.Condition.LESS_THAN_OR_EQUAL_TO
                    )
            );
        }
    }

    @Column(name=LAST_UPDATED)
    @IsDataSourceField(fieldSupplier = LastUpdatedField.class)
    public Long getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(Long lastUpdated) {
        this.lastUpdated = lastUpdated;
    }

    public static class ContentField implements Supplier<DataSourceField> {

        @Override
        public DataSourceField get() {
            return new DataSourceField(
                    DataSourceField.DataSourceFieldType.FIELD,
                    Annotation.CONTENT,
                    true,
                    Arrays.asList(
                            ExpressionTerm.Condition.EQUALS,
                            ExpressionTerm.Condition.CONTAINS
                    )
            );
        }
    }

    @Column(name=CONTENT)
    @IsDataSourceField(fieldSupplier = ContentField.class)
    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public static class UpdatedByField implements Supplier<DataSourceField> {

        @Override
        public DataSourceField get() {
            return new DataSourceField(
                    DataSourceField.DataSourceFieldType.FIELD,
                    Annotation.UPDATED_BY,
                    true,
                    Arrays.asList(
                            ExpressionTerm.Condition.EQUALS,
                            ExpressionTerm.Condition.CONTAINS,
                            ExpressionTerm.Condition.IN,
                            ExpressionTerm.Condition.IN_DICTIONARY
                    )
            );
        }
    }

    @Column(name=UPDATED_BY)
    @IsDataSourceField(fieldSupplier = UpdatedByField.class)
    public String getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("Annotation{");
        sb.append(super.toString()).append('\'');
        sb.append(", id='").append(assignTo).append('\'');
        sb.append(", assignTo='").append(assignTo).append('\'');
        sb.append(", lastUpdated=").append(lastUpdated);
        sb.append(", updatedBy='").append(updatedBy).append('\'');
        sb.append(", status=").append(status);
        sb.append(", content='").append(content).append('\'');
        sb.append('}');
        return sb.toString();
    }

    public static final class Builder extends QueryableEntity.ABuilder<Annotation, Builder> {

        public Builder() {
            super(new Annotation());
        }

        public Builder id(final String value) {
            this.instance.setId(value);
            return self();
        }

        public Builder assignTo(final String value) {
            this.instance.setAssignTo(value);
            return self();
        }

        public Builder lastUpdated(final Long value) {
            this.instance.setLastUpdated(value);
            return self();
        }

        public Builder updatedBy(final String value) {
            this.instance.setUpdatedBy(value);
            return self();
        }

        public Builder status(final Status value) {
            this.instance.setStatus(value);
            return self();
        }

        public Builder content(final String value) {
            this.instance.setContent(value);
            return self();
        }

        @Override
        protected Builder self() {
            return this;
        }
    }
}
