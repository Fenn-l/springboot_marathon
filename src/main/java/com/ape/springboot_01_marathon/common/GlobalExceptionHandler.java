package com.ape.springboot_01_marathon.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理。
 * <p>
 * 核心原则：<b>能被预见的失败都要翻译成人话</b>，只有真正的代码 bug 才返回「系统繁忙」。
 * 用户看到「系统繁忙，请稍后重试」是无法采取任何行动的，等于没提示。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 业务异常：可预期，直接把 msg 吐给前端 */
    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusiness(BusinessException e) {
        log.warn("业务异常：{}", e.getMessage());
        return Result.error(e.getCode(), e.getMessage());
    }

    /** @RequestBody 参数校验失败 */
    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public Result<Void> handleValid(BindException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(f -> f.getField() + " " + f.getDefaultMessage())
                .orElse("参数校验失败");
        return Result.error(400, msg);
    }

    /**
     * 外键约束冲突。
     * <p>
     * MySQL 报错原文：
     * {@code Cannot delete or update a parent row: a foreign key constraint fails
     * (`marathon_db`.`registration`, CONSTRAINT `registration_ibfk_2` FOREIGN KEY (`runner_id`) ...)}
     * <p>
     * 直接把这句英文返回给用户毫无意义，这里翻译成「该选手还有报名记录，请先取消报名」。
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public Result<Void> handleDataIntegrity(DataIntegrityViolationException e) {
        log.warn("外键约束冲突：{}", e.getMessage());

        // 从 MySQL 错误消息里提取出是哪张表被引用
        String table = extractTableName(e.getMessage());

        String msg;
        if ("registration".equals(table)) {
            msg = "该选手（或赛事）仍有报名记录，无法删除。请先在「报名管理」中取消相关报名";
        } else if ("result".equals(table)) {
            msg = "该报名记录已有成绩，无法删除";
        } else {
            msg = "该数据仍被其他数据引用，无法删除。请先清理关联数据";
        }
        return Result.error(400, msg);
    }

    /**
     * 从 MySQL 错误消息里抠出表名。
     * <p>
     * 消息形如：{@code (db`.`registration`, CONSTRAINT `registration_ibfk_2` FOREIGN KEY ...)}
     * 取第一个反引号对之间的内容即可。
     */
    private String extractTableName(String message) {
        if (message == null) {
            return null;
        }
        int first = message.indexOf('`');
        if (first < 0) {
            return null;
        }
        int second = message.indexOf('`', first + 1);
        if (second < 0) {
            return null;
        }
        // 形如 db`.`table，取后面那个
        String segment = message.substring(first + 1, second);
        int dot = segment.indexOf('.');
        return dot >= 0 ? segment.substring(dot + 1) : segment;
    }

    /**
     * 兜底。
     * <p>
     * 走到这里说明是代码 bug 或未预期的 SQL 问题。
     * 开发期把异常类型也返回前端，省得每次都去翻控制台；
     * 上线后应改成只记日志、不外泄细节。
     */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        log.error("系统异常", e);
        return Result.error("系统繁忙，请稍后重试");
    }
}