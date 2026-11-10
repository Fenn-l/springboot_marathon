package com.ape.springboot_01_marathon;

import com.ape.springboot_01_marathon.pojo.MarathonEvent;
import com.ape.springboot_01_marathon.pojo.Registration;
import com.ape.springboot_01_marathon.pojo.ResultRecord;
import com.ape.springboot_01_marathon.pojo.Runner;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
// PER_CLASS：整个测试类共用一个实例，成员变量（eventId 等）才能在 @Order 方法之间传递，
// 同时 @BeforeAll 不用加 static。默认的 PER_METHOD 每个方法都是新实例，变量传不过去。
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class Springboot01MarathonApplicationTests {
    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    private Long eventId;
    private Long runnerId;
    private Long registrationId;
    /**
     * 整个测试类开始前清一次数据。
     * 必须是 @BeforeAll，不能用 @BeforeEach。
     * 因为 @Order 靠成员变量在测试方法之间传值（eventId / runnerId / registrationId），
     * 如果每个方法前都清库，@Order(1) 建的赛事会被 @Order(2) 之前的 clean() 删掉，
     * 导致 @Order(3) 提交报名时 eventId 指向的记录已不存在。
     */
    @BeforeAll
    void cleanDb() {
        jdbcTemplate.update("DELETE FROM `result`");
        jdbcTemplate.update("DELETE FROM registration");
        jdbcTemplate.update("DELETE FROM marathon_event");
        jdbcTemplate.update("DELETE FROM runner");
        System.out.println(">>> 已清空测试数据");
    }
    /** 1. 新增赛事 */
    @Test
    @Order(1)
    void testAddEvent() {
        MarathonEvent event = new MarathonEvent();
        event.setEventName("2026西安国际马拉松");
        event.setEventType(1);
        event.setEventDate(LocalDate.of(2026, 11, 15));
        event.setStartLocation("西安大明宫国家遗址公园");
        event.setDistanceKm(new BigDecimal("42.2"));
        event.setQuota(10);
        event.setEntryFee(new BigDecimal("200.00"));
        event.setStatus(0);

        Map<String, Object> resp = post("/api/event/add", event);
        System.out.println("新增赛事 -> " + resp);
        eventId = ((Number) ((Map<?, ?>) resp.get("data")).get("id")).longValue();
    }

    /** 2. 新增选手 */
    @Test
    @Order(2)
    void testAddRunner() {
        Runner runner = new Runner();
        runner.setName("张三");
        runner.setGender(1);
        runner.setIdCard("610104199503101234");
        runner.setPhone("13800138000");
        runner.setBirthDate(LocalDate.of(1995, 3, 10));
        runner.setEmergencyContact("李四,13900139000");

        Map<String, Object> resp = post("/api/runner/add", runner);
        System.out.println("新增选手 -> " + resp);
        runnerId = ((Number) ((Map<?, ?>) resp.get("data")).get("id")).longValue();
    }
    /** 3. 提交报名 */
    @Test
    @Order(3)
    void testSubmit() {
        Registration reg = new Registration();
        reg.setEventId(eventId);
        reg.setRunnerId(runnerId);

        Map<String, Object> resp = post("/api/register/submit", reg);
        System.out.println("提交报名 -> " + resp);
        registrationId = ((Number) ((Map<?, ?>) resp.get("data")).get("registrationId")).longValue();
    }

    /** 4. 重复报名应被拒绝（反向用例：期望业务失败） */
    @Test
    @Order(4)
    void testDuplicateSubmit() {
        Registration reg = new Registration();
        reg.setEventId(eventId);
        reg.setRunnerId(runnerId);

        // 用 postExpectFail，不能用 post —— post 遇失败会直接抛 AssertionError
        Map<String, Object> resp = postExpectFail("/api/register/submit", reg, "重复报名");
        System.out.println("重复报名 -> " + resp + "（符合预期，被拒绝）");
    }

    /** 5. 分配号码布 */
    @Test
    @Order(5)
    void testAssignBib() {
        // 后端 /api/register/bib 是 @PutMapping，必须用 PUT 请求，不能用 getForObject
        String url = "/api/register/bib?id=" + registrationId + "&bibNumber=A0001";
        System.out.println("分配号码布 -> "
                + rest.exchange(url, HttpMethod.PUT, null, Map.class).getBody());
    }

    /** 6. 录入成绩 */
    @Test
    @Order(6)
    void testInputResult() {
        ResultRecord record = new ResultRecord();
        record.setRegistrationId(registrationId);
        record.setChipTime(12800);
        record.setNetTime(12550);
        record.setFinishStatus(1);
        record.setFinishTime(LocalDateTime.of(2026, 11, 15, 10, 29, 10));

        System.out.println("录入成绩 -> " + post("/api/result/input", record));
    }

    /** 7. 赛事总排名 */
    @Test
    @Order(7)
    void testEventRanking() {
        System.out.println("总排名 -> "
                + rest.getForObject("/api/result/eventRanking/" + eventId, Map.class));
    }

    /** 8. 男女分组排名 */
    @Test
    @Order(8)
    void testGenderRanking() {
        System.out.println("男女分组 -> "
                + rest.getForObject("/api/result/genderRanking/" + eventId, Map.class));
    }

    /** 9. 年龄分组排名 */
    @Test
    @Order(9)
    void testAgeRanking() {
        System.out.println("年龄分组 -> "
                + rest.getForObject("/api/result/ageRanking/" + eventId, Map.class));
    }

    /** 10. 统计看板 */
    @Test
    @Order(10)
    void testStatistics() {
        System.out.println("统计总览 -> "
                + rest.getForObject("/api/statistics/eventOverview/" + eventId, Map.class));
    }

    /** 11. 赛事列表分页 */
    @Test
    @Order(11)
    void testEventList() {
        System.out.println("赛事列表 -> "
                + rest.getForObject("/api/event/list?pageNum=1&pageSize=10&status=0", Map.class));
    }

    /** 12. 删除有报名的赛事应失败（反向用例：期望业务失败） */
    @Test
    @Order(12)
    void testDeleteEventShouldFail() {
        // 赛事下已有报名记录，Service 会抛"不允许删除"
        ResponseEntity<Map> resp = rest.exchange("/api/event/delete/" + eventId,
                HttpMethod.DELETE, null, Map.class);

        System.out.println("删除赛事 -> " + resp.getBody());
        Assertions.assertNotEquals(200, ((Number) resp.getBody().get("code")).intValue(),
                "有报名记录的赛事删除应该失败");
        Assertions.assertTrue(String.valueOf(resp.getBody().get("msg")).contains("报名记录"),
                "错误信息应说明是因为存在报名记录，实际是：" + resp.getBody().get("msg"));
    }

    // ==================== 工具方法 ====================

    /**
     * POST 请求，【期望成功】。
     * 注意：postForObject 返回的已经是响应体本身，不是 ResponseEntity，所以不需要 .getBody()
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> post(String url, Object body) {
        Map<String, Object> resp = postRaw(url, body);
        // 业务失败时 data 一定是 null，后面强转会 NPE，报错看不出真正原因。
        // 在这里直接断言，把后端返回的 msg 抛出来，一眼就能看到是哪个校验没过。
        if (resp == null || !Integer.valueOf(200).equals(resp.get("code"))) {
            throw new AssertionError("接口 " + url + " 本应成功却失败了，返回：" + resp);
        }
        return resp;
    }

    /**
     * POST 请求，【期望业务失败】。
     * 用于「重复报名应被拒绝」这类反向用例：不能走 post()，因为 post() 遇失败会抛断言错误。
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> postExpectFail(String url, Object body, String expectMsgPart) {
        Map<String, Object> resp = postRaw(url, body);
        if (resp == null) {
            throw new AssertionError("接口 " + url + " 返回 null，期望业务失败");
        }
        // 反向用例真正的断言：code 必须是失败，且 msg 包含预期关键词
        if (Integer.valueOf(200).equals(resp.get("code"))) {
            throw new AssertionError("接口 " + url + " 本应被拒绝却成功了，返回：" + resp);
        }
        String msg = String.valueOf(resp.get("msg"));
        if (!msg.contains(expectMsgPart)) {
            throw new AssertionError("接口 " + url + " 失败原因不符合预期，"
                    + "期望 msg 包含 [" + expectMsgPart + "]，实际 msg = " + msg);
        }
        return resp;
    }

    /** 只发请求不做任何断言，原样返回 */
    @SuppressWarnings("unchecked")
    private Map<String, Object> postRaw(String url, Object body) {
        return (Map<String, Object>) rest.postForObject(url, body, Map.class);
    }

}
