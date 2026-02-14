package com.eval.gameeval.aspect;


import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;


import java.lang.reflect.Method;
import java.util.Arrays;


@Component
@Slf4j
@Aspect
public class LogAspect {


    /**
     * 切面：拦截所有带@LogRecord注解的方法
     */
    @Around("@annotation(LogRecord)")
    public Object doAround(ProceedingJoinPoint joinPoint) throws Throwable {
        long startTime = System.currentTimeMillis();

        // 获取方法签名
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();

        // 获取注解信息
        LogRecord logRecord = method.getAnnotation(LogRecord.class);
        String operation = logRecord.value();
        String module = logRecord.module();

        // 获取方法信息
        String className = joinPoint.getTarget().getClass().getSimpleName();
        String methodName = method.getName();

        try {
            // 记录方法开始
            log.info("【{}】{} - 方法开始执行: {}.{}，参数: {}",
                    module, operation, className, methodName,
                    Arrays.toString(joinPoint.getArgs()));

            // 执行方法
            Object result = joinPoint.proceed();

            // 记录方法结束
            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;

            log.info("【{}】{} - 方法执行完成: {}.{}，耗时: {}ms，返回值: {}",
                    module, operation, className, methodName,
                    duration, result);

            return result;

        } catch (Exception e) {
            // 记录异常
            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;

            log.error("【{}】{} - 方法执行异常: {}.{}，耗时: {}ms，异常信息: {}",
                    module, operation, className, methodName,
                    duration, e.getMessage(), e);

            throw e;
        }
    }

}
