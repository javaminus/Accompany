package com.javaminus.FuckAIGC.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Result {
    private Boolean success;
    private String errorMsg;
    private Long total;
    private Object data;

    private ReturnCodeEnum returnCodeEnum;

    public static Result ok(){
        return new Result(true, null, null, null,null);
    }
    public static Result ok(Object data){
        return new Result(true, null, null, data,null);
    }
    public static Result ok(List<?> data, Long total){
        return new Result(true, null, total, data,null);
    }
    public static Result fail(String errorMsg){
        return new Result(false, errorMsg, null, null,null);
    }

    public static Result fail(ReturnCodeEnum returnCodeEnum){
        return new Result(false, null, null, null,returnCodeEnum);
    }
    public static Result fail(){
        return new Result(false, null, null, null,null);
    }
}
