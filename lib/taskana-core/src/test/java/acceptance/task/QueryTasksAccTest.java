package acceptance.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static pro.taskana.task.api.TaskCustomField.CUSTOM_7;

import acceptance.AbstractAccTest;
import acceptance.TaskTestMapper;
import acceptance.TaskanaEngineProxy;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.internal.stubbing.answers.CallsRealMethods;

import pro.taskana.common.api.exceptions.InvalidArgumentException;
import pro.taskana.common.internal.util.CollectionUtil;
import pro.taskana.common.internal.util.Triplet;
import pro.taskana.common.test.security.JaasExtension;
import pro.taskana.common.test.security.WithAccessId;
import pro.taskana.task.api.TaskCustomField;
import pro.taskana.task.api.models.Task;
import pro.taskana.task.api.models.TaskSummary;
import pro.taskana.task.internal.models.TaskImpl;

/** Acceptance test for all "query tasks with sorting" scenarios. */
@ExtendWith(JaasExtension.class)
class QueryTasksAccTest extends AbstractAccTest {

  @BeforeEach
  void before() throws Exception {
    // required if single tests modify database
    // TODO split test class into readOnly & modifying tests to improve performance
    resetDb(false);
  }

  @WithAccessId(user = "user-1-1")
  @Test
  void should_SetOwnerLongNameOfTask_When_PropertyEnabled() throws Exception {
    taskanaEngineConfiguration.setAddAdditionalUserInfo(true);
    List<TaskSummary> tasks =
        taskService.createTaskQuery().idIn("TKI:000000000000000000000000000000000000").list();

    assertThat(tasks).hasSize(1);
    String longName = taskanaEngine.getUserService().getUser(tasks.get(0).getOwner()).getLongName();
    assertThat(tasks.get(0)).extracting(TaskSummary::getOwnerLongName).isEqualTo(longName);
  }

  @WithAccessId(user = "user-1-1")
  @Test
  void should_NotSetOwnerLongNameOfTask_When_PropertyDisabled() {
    taskanaEngineConfiguration.setAddAdditionalUserInfo(false);
    List<TaskSummary> tasks =
        taskService.createTaskQuery().idIn("TKI:000000000000000000000000000000000000").list();

    assertThat(tasks).hasSize(1);
    assertThat(tasks.get(0)).extracting(TaskSummary::getOwnerLongName).isNull();
  }

  @WithAccessId(user = "user-1-2")
  @Test
  void should_SetOwnerLongNameOfTask_When_FilteringWithOwnerLongNameIn() {
    taskanaEngineConfiguration.setAddAdditionalUserInfo(false);
    String longName = "Eifrig, Elena - (user-1-2)";
    List<TaskSummary> tasks = taskService.createTaskQuery().ownerLongNameIn(longName).list();

    assertThat(tasks)
        .hasSize(23)
        .extracting(TaskSummary::getOwnerLongName)
        .doesNotContainNull()
        .containsOnly(longName);
  }

  @WithAccessId(user = "user-1-1")
  @Test
  void should_SetOwnerLongNameOfTask_When_FilteringWithOwnerLongNameNotIn() {
    taskanaEngineConfiguration.setAddAdditionalUserInfo(false);
    List<TaskSummary> tasks =
        taskService
            .createTaskQuery()
            .idIn(
                "TKI:000000000000000000000000000000000000",
                "TKI:000000000000000000000000000000000027")
            .ownerLongNameNotIn("Eifrig, Elena - (user-1-2)")
            .list();

    assertThat(tasks).hasSize(1);
    assertThat(tasks.get(0))
        .extracting(TaskSummary::getOwnerLongName)
        .isEqualTo("Mustermann, Max - (user-1-1)");
  }

  @WithAccessId(user = "user-1-2")
  @Test
  void should_SetOwnerLongNameOfTask_When_FilteringWithOwnerLongNameLike() {
    taskanaEngineConfiguration.setAddAdditionalUserInfo(false);
    List<TaskSummary> tasks = taskService.createTaskQuery().ownerLongNameLike("%1-2%").list();

    assertThat(tasks)
        .hasSize(23)
        .extracting(TaskSummary::getOwnerLongName)
        .doesNotContainNull()
        .containsOnly("Eifrig, Elena - (user-1-2)");
  }

  @WithAccessId(user = "user-1-2")
  @Test
  void should_SetOwnerLongNameOfTask_When_FilteringWithOwnerLongNameNotLike() throws Exception {
    taskanaEngineConfiguration.setAddAdditionalUserInfo(false);
    List<TaskSummary> tasks = taskService.createTaskQuery().ownerLongNameNotLike("%1-1%").list();

    assertThat(tasks)
        .hasSize(23)
        .extracting(TaskSummary::getOwnerLongName)
        .doesNotContainNull()
        .containsOnly("Eifrig, Elena - (user-1-2)");
  }

  @WithAccessId(user = "admin")
  @Test
  void should_SetOwnerLongNameOfTaskToNull_When_OwnerNotExistingAsUserInDatabase() {
    taskanaEngineConfiguration.setAddAdditionalUserInfo(true);
    List<TaskSummary> tasks =
        taskService.createTaskQuery().idIn("TKI:000000000000000000000000000000000041").list();

    assertThat(tasks).hasSize(1);
    ThrowingCallable call =
        () -> taskanaEngine.getUserService().getUser(tasks.get(0).getOwner()).getLongName();
    assertThatThrownBy(call).isInstanceOf(UserNotFoundException.class);
    assertThat(tasks.get(0)).extracting(TaskSummary::getOwnerLongName).isNull();
  }

  @WithAccessId(user = "admin")
  @Test
  void should_OrderByOwnerLongName_When_QueryingTask() {
    taskanaEngineConfiguration.setAddAdditionalUserInfo(false);
    List<TaskSummary> tasks =
        taskService
            .createTaskQuery()
            .stateIn(TaskState.CLAIMED)
            .ownerNotIn("user-b-1")
            .orderByOwnerLongName(ASCENDING)
            .list();
    assertThat(tasks).extracting(TaskSummary::getOwnerLongName).hasSize(17).isSorted();

    tasks =
        taskService
            .createTaskQuery()
            .stateIn(TaskState.CLAIMED)
            .ownerNotIn("user-b-1")
            .orderByOwnerLongName(DESCENDING)
            .list();
    assertThat(tasks)
        .hasSize(17)
        .extracting(TaskSummary::getOwnerLongName)
        .isSortedAccordingTo(Comparator.reverseOrder());
  }

  @WithAccessId(user = "admin")
  @Test
  void should_ListValues_For_OwnerLongName() {
    taskanaEngineConfiguration.setAddAdditionalUserInfo(false);
    List<String> longNames =
        taskService.createTaskQuery().listValues(TaskQueryColumnName.OWNER_LONG_NAME, ASCENDING)
            .stream()
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    assertThat(longNames)
        .hasSize(2)
        .isSorted()
        .containsExactly("Eifrig, Elena - (user-1-2)", "Mustermann, Max - (user-1-1)");

    longNames =
        taskService.createTaskQuery().listValues(TaskQueryColumnName.OWNER_LONG_NAME, DESCENDING)
            .stream()
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    ;
    assertThat(longNames)
        .hasSize(2)
        .contains("Mustermann, Max - (user-1-1)", "Eifrig, Elena - (user-1-2)")
        .isSortedAccordingTo(Comparator.reverseOrder());
  }

  @WithAccessId(user = "user-1-2")
  @Test
  void should_ListValuesCorrectly_When_FilteringWithOwnerLongName() {
    taskanaEngineConfiguration.setAddAdditionalUserInfo(false);
    String longName = "Eifrig, Elena - (user-1-2)";
    List<String> listedValues =
        taskService
            .createTaskQuery()
            .ownerLongNameIn(longName)
            .orderByTaskId(null)
            .listValues(TaskQueryColumnName.ID, null);
    assertThat(listedValues).hasSize(23);

    List<TaskSummary> query =
        taskService.createTaskQuery().ownerLongNameIn(longName).orderByTaskId(null).list();
    assertThat(query).hasSize(23).extracting(TaskSummary::getId).isEqualTo(listedValues);
  }

  @WithAccessId(user = "user-1-2")
  @Test
  void should_CountCorrectly_When_FilteringWithOwnerLongName() {
    taskanaEngineConfiguration.setAddAdditionalUserInfo(false);
    String longName = "Eifrig, Elena - (user-1-2)";
    long count = taskService.createTaskQuery().ownerLongNameIn(longName).count();
    assertThat(count).isEqualTo(23);

    List<TaskSummary> query = taskService.createTaskQuery().ownerLongNameIn(longName).list();
    assertThat(query.size()).isEqualTo(count);
  }

  @WithAccessId(user = "admin")
  @Test
  void should_SplitTaskListIntoChunksOf32000_When_AugmentingTasksAfterTaskQuery() {
    try (MockedStatic<CollectionUtil> listUtilMock =
        Mockito.mockStatic(CollectionUtil.class, new CallsRealMethods())) {
      TASK_SERVICE.createTaskQuery().list();

      listUtilMock.verify(() -> CollectionUtil.partitionBasedOnSize(any(), eq(32000)));
    }
  }

  @Nested
  @TestInstance(Lifecycle.PER_CLASS)
  class CustomAttributeTest {

    @WithAccessId(user = "admin")
    @TestFactory
    Stream<DynamicTest> should_ReturnCorrectResults_When_QueryingForCustomXStatements() {
      List<Triplet<TaskCustomField, String[], Integer>> list =
          List.of(
              Triplet.of(
                  TaskCustomField.CUSTOM_1, new String[] {"custom%", "p%", "%xyz%", "efg"}, 3),
              Triplet.of(TaskCustomField.CUSTOM_2, new String[] {"custom%", "a%"}, 2),
              Triplet.of(TaskCustomField.CUSTOM_3, new String[] {"ffg"}, 1),
              Triplet.of(TaskCustomField.CUSTOM_4, new String[] {"%ust%", "%ty"}, 2),
              Triplet.of(TaskCustomField.CUSTOM_5, new String[] {"ew", "al"}, 6),
              Triplet.of(TaskCustomField.CUSTOM_6, new String[] {"%custom6%", "%vvg%", "11%"}, 5),
              Triplet.of(TaskCustomField.CUSTOM_7, new String[] {"%"}, 2),
              Triplet.of(TaskCustomField.CUSTOM_8, new String[] {"%"}, 2),
              Triplet.of(TaskCustomField.CUSTOM_9, new String[] {"%"}, 2),
              Triplet.of(TaskCustomField.CUSTOM_10, new String[] {"%"}, 3),
              Triplet.of(TaskCustomField.CUSTOM_11, new String[] {"%"}, 3),
              Triplet.of(TaskCustomField.CUSTOM_12, new String[] {"%"}, 3),
              Triplet.of(TaskCustomField.CUSTOM_13, new String[] {"%"}, 3),
              Triplet.of(TaskCustomField.CUSTOM_14, new String[] {"%"}, 88),
              Triplet.of(TaskCustomField.CUSTOM_15, new String[] {"%"}, 3),
              Triplet.of(TaskCustomField.CUSTOM_16, new String[] {"%"}, 3));
      assertThat(list).hasSameSizeAs(TaskCustomField.values());

      return DynamicTest.stream(
          list.iterator(),
          t -> t.getLeft().name(),
          t -> testQueryForCustomXLikeAndIn(t.getLeft(), t.getMiddle(), t.getRight()));
    }

    void testQueryForCustomXLikeAndIn(
        TaskCustomField customField, String[] searchArguments, int expectedResult)
        throws Exception {
      List<TaskSummary> results =
          TASK_SERVICE.createTaskQuery().customAttributeLike(customField, searchArguments).list();
      assertThat(results).hasSize(expectedResult);

      String[] customAttributes =
          results.stream().map(t -> t.getCustomAttribute(customField)).toArray(String[]::new);

      List<TaskSummary> result2 =
          TASK_SERVICE.createTaskQuery().customAttributeIn(customField, customAttributes).list();
      assertThat(result2).hasSize(expectedResult);
    }

    @WithAccessId(user = "admin")
    @TestFactory
    Stream<DynamicTest> should_ReturnCorrectResults_When_QueryingForCustomXNotIn() {
      // carefully constructed to always return exactly 2 results
      List<Triplet<TaskCustomField, String[], Integer>> list =
          List.of(
              Triplet.of(TaskCustomField.CUSTOM_1, new String[] {"custom1"}, 2),
              Triplet.of(TaskCustomField.CUSTOM_2, new String[] {"%"}, 2),
              Triplet.of(TaskCustomField.CUSTOM_3, new String[] {"custom3"}, 2),
              Triplet.of(TaskCustomField.CUSTOM_4, new String[] {"%"}, 2),
              Triplet.of(TaskCustomField.CUSTOM_5, new String[] {"ew", "al", "el"}, 2),
              Triplet.of(TaskCustomField.CUSTOM_6, new String[] {"11", "vvg"}, 2),
              Triplet.of(TaskCustomField.CUSTOM_7, new String[] {"%"}, 2),
              Triplet.of(TaskCustomField.CUSTOM_8, new String[] {"%"}, 2),
              Triplet.of(TaskCustomField.CUSTOM_9, new String[] {"%"}, 2),
              Triplet.of(TaskCustomField.CUSTOM_10, new String[] {"custom10"}, 2),
              Triplet.of(TaskCustomField.CUSTOM_11, new String[] {"custom11"}, 2),
              Triplet.of(TaskCustomField.CUSTOM_12, new String[] {"custom12"}, 2),
              Triplet.of(TaskCustomField.CUSTOM_13, new String[] {"custom13"}, 2),
              Triplet.of(TaskCustomField.CUSTOM_14, new String[] {"abc"}, 2),
              Triplet.of(TaskCustomField.CUSTOM_15, new String[] {"custom15"}, 2),
              Triplet.of(TaskCustomField.CUSTOM_16, new String[] {"custom16"}, 2));
      assertThat(list).hasSameSizeAs(TaskCustomField.values());

      return DynamicTest.stream(
          list.iterator(),
          t -> t.getLeft().name(),
          t -> testQueryForCustomXNotIn(t.getLeft(), t.getMiddle(), t.getRight()));
    }

    void testQueryForCustomXNotIn(
        TaskCustomField customField, String[] searchArguments, int expectedCount) throws Exception {
      long results =
          TASK_SERVICE.createTaskQuery().customAttributeNotIn(customField, searchArguments).count();
      assertThat(results).isEqualTo(expectedCount);
    }

    @WithAccessId(user = "admin")
    @Test
    void testQueryForCustom7WithExceptionInLike() {
      assertThatThrownBy(() -> TASK_SERVICE.createTaskQuery().customAttributeLike(CUSTOM_7).list())
          .isInstanceOf(InvalidArgumentException.class);
    }

    @WithAccessId(user = "admin")
    @Test
    void testQueryForCustom7WithExceptionInIn() throws Exception {
      List<TaskSummary> results =
          TASK_SERVICE.createTaskQuery().customAttributeLike(CUSTOM_7, "fsdhfshk%").list();
      assertThat(results).isEmpty();

      assertThatThrownBy(() -> TASK_SERVICE.createTaskQuery().customAttributeIn(CUSTOM_7).list())
          .isInstanceOf(InvalidArgumentException.class);
    }

    @WithAccessId(user = "admin")
    @Test
    void testQueryForCustom7WithException() throws Exception {
      List<TaskSummary> results =
          TASK_SERVICE.createTaskQuery().customAttributeLike(CUSTOM_7, "%").list();
      assertThat(results).hasSize(2);

      String[] ids =
          results.stream().map(t -> t.getCustomAttribute(CUSTOM_7)).toArray(String[]::new);

      List<TaskSummary> result2 =
          TASK_SERVICE.createTaskQuery().customAttributeIn(CUSTOM_7, ids).list();
      assertThat(result2).hasSize(2);
    }

    @WithAccessId(user = "admin")
    @Test
    void testQueryTaskByCustomAttributes() throws Exception {
      Task newTask = TASK_SERVICE.newTask("USER-1-1", "DOMAIN_A");
      newTask.setPrimaryObjRef(
          createObjectReference("COMPANY_A", "SYSTEM_A", "INSTANCE_A", "VNR", "1234567"));
      newTask.setClassificationKey("T2100");
      Map<String, String> customAttributesForCreate =
          createSimpleCustomPropertyMap(20000); // about 1 Meg
      newTask.setCustomAttributeMap(customAttributesForCreate);
      Task createdTask = TASK_SERVICE.createTask(newTask);

      assertThat(createdTask).isNotNull();
      // query the task by custom attributes
      TaskanaEngineProxy engineProxy = new TaskanaEngineProxy(taskanaEngine);
      try {
        SqlSession session = engineProxy.getSqlSession();
        Configuration config = session.getConfiguration();
        if (!config.hasMapper(TaskTestMapper.class)) {
          config.addMapper(TaskTestMapper.class);
        }

        TaskTestMapper mapper = session.getMapper(TaskTestMapper.class);
        engineProxy.openConnection();
        List<TaskImpl> queryResult =
            mapper.selectTasksByCustomAttributeLike("%Property Value of Property_1339%");

        assertThat(queryResult).hasSize(1);
        Task retrievedTask = queryResult.get(0);

        assertThat(retrievedTask.getId()).isEqualTo(createdTask.getId());

        // verify that the map is correctly retrieved from the database
        Map<String, String> customAttributesFromDb = retrievedTask.getCustomAttributeMap();
        assertThat(customAttributesFromDb).isEqualTo(customAttributesForCreate);

      } finally {
        engineProxy.returnConnection();
      }
    }
  }
}
