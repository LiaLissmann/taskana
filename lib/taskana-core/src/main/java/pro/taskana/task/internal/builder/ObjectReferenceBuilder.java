package pro.taskana.task.internal.builder;

import pro.taskana.task.api.models.ObjectReference;

public class ObjectReferenceBuilder {

  private final ObjectReference objectReference = new ObjectReference();

  private ObjectReferenceBuilder() {}

  public static ObjectReferenceBuilder newObjectReference() {
    return new ObjectReferenceBuilder();
  }

  public ObjectReferenceBuilder company(String company) {
    objectReference.setCompany(company);
    return this;
  }

  public ObjectReferenceBuilder system(String system) {
    objectReference.setSystem(system);
    return this;
  }

  public ObjectReferenceBuilder systemInstance(String systemInstance) {
    objectReference.setSystemInstance(systemInstance);
    return this;
  }

  public ObjectReferenceBuilder type(String type) {
    objectReference.setType(type);
    return this;
  }

  public ObjectReferenceBuilder value(String value) {
    objectReference.setValue(value);
    return this;
  }

  public ObjectReference build() {
    return objectReference.copy();
  }
}
