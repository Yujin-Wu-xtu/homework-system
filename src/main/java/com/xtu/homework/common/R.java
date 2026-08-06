package com.xtu.homework.common;

import java.util.HashMap;
import java.util.Map;

/**
 * 统一响应结果封装类
 * 所有 API 接口统一返回格式: {"code": 200, "msg": "success", "data": {...}}
 */
public class R extends HashMap<String, Object> {

    public static R ok() {
        R r = new R();
        r.put("code", 200);
        r.put("msg", "success");
        return r;
    }

    public static R ok(String msg) {
        R r = ok();
        r.put("msg", msg);
        return r;
    }

    public static R error(int code, String msg) {
        R r = new R();
        r.put("code", code);
        r.put("msg", msg);
        return r;
    }

    public R put(String key, Object value) {
        super.put(key, value);
        return this;
    }

    public R data(Object data) {
        this.put("data", data);
        return this;
    }

    public static R badRequest(String msg) { return error(400, msg); }
    public static R unauthorized(String msg) { return error(401, msg); }
    public static R forbidden(String msg) { return error(403, msg); }
    public static R notFound(String msg) { return error(404, msg); }
    public static R conflict(String msg) { return error(409, msg); }
    public static R serverError(String msg) { return error(500, msg); }
}
