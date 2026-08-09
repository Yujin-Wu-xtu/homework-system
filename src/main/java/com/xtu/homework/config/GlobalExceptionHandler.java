package com.xtu.homework.config;

import com.xtu.homework.common.R;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 全局异常处理：Controller 层未捕获的异常统一转为 R 格式。
 * - 消除 Spring 默认错误页/堆栈泄露（安全：不向客户端暴露内部异常信息）
 * - @Valid 校验失败、参数类型错误、文件超限给出用户可理解的提示
 * - 保持越权语义：AccessDeniedException → HTTP 403；未认证 → HTTP 401
 *
 * 注意：URL 级鉴权（SecurityConfig .hasRole）在 Filter 链抛出，不经过本处理器；
 * 方法级 @PreAuthorize 在 Controller 调用层抛出，会被本处理器接管。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** @Valid 校验失败（如 LoginDto 空用户名 / HomeworkAssignDto 缺截止时间）——取第一条字段错误消息 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public R handleValidation(MethodArgumentNotValidException e) {
        FieldError fe = e.getBindingResult().getFieldError();
        String msg = fe != null ? fe.getDefaultMessage() : "参数校验失败";
        return R.badRequest(msg);
    }

    /** 路径/查询参数类型不匹配（如 /api/admin/questions/abc → id 应为数字） */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public R handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return R.badRequest("参数 " + e.getName() + " 格式不正确");
    }

    /** 缺少必填请求参数（@RequestParam required） */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public R handleMissingParam(MissingServletRequestParameterException e) {
        return R.badRequest("缺少参数: " + e.getParameterName());
    }

    /** 文件超过大小限制（异常发生在 multipart 解析阶段、进入 Controller 之前，Controller 内 catch 接不到） */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public R handleMaxUpload(MaxUploadSizeExceededException e) {
        return R.badRequest("文件大小超过限制");
    }

    /** 方法级 @PreAuthorize 越权 → HTTP 403（保持与 URL 级鉴权一致的语义） */
    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public R handleAccessDenied(AccessDeniedException e) {
        return R.forbidden("无权限访问");
    }

    /** 未认证访问方法级保护接口 → HTTP 401 */
    @ExceptionHandler(AuthenticationException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public R handleAuthentication(AuthenticationException e) {
        return R.unauthorized("未登录或登录已过期");
    }

    /** 静态资源 404：不拦截，交给 Spring 默认 404 处理（避免 SPA/资源请求被兜底 handler 误转 500） */
    @ExceptionHandler(NoResourceFoundException.class)
    public void handleNoResource(NoResourceFoundException e) throws NoResourceFoundException {
        throw e;
    }

    /** 兜底：任何未捕获异常统一转 500 提示，日志记录详情（不向客户端泄露堆栈/内部错误） */
    @ExceptionHandler(Exception.class)
    public R handleOther(Exception e) {
        log.error("Unhandled exception: ", e);
        return R.serverError("系统繁忙，请稍后重试");
    }
}
