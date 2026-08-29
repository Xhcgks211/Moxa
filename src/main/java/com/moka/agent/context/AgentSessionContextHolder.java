package com.moka.agent.context;

import java.util.function.Supplier;

/**
 * 线程本地上下文持有者，用于在 Spring prototype bean 构建时传递 {@link AgentSessionContext}。
 *
 * <p>典型用法：Controller 在调用 Agent 前 set，请求结束前 clear；Agent Bean 工厂在 build 时
 * 通过 {@link #get()} 拿到上下文后注入到 {@code ToolExecutionContext}，供工具方法自动注入。</p>
 */
public final class AgentSessionContextHolder {

    private static final ThreadLocal<AgentSessionContext> HOLDER = new ThreadLocal<>();

    private AgentSessionContextHolder() {
    }

    public static void set(AgentSessionContext context) {
        HOLDER.set(context);
    }

    public static AgentSessionContext get() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }

    /**
     * 将当前线程的上下文绑定到 supplier 执行过程中（用于异步 / 跨线程场景），
     * 执行完毕后还原原有上下文。
     */
    public static <T> Supplier<T> bind(Supplier<T> supplier) {
        AgentSessionContext captured = get();
        if (captured == null) {
            return supplier;
        }
        return () -> callWith(captured, supplier);
    }

    public static <T> T callWith(AgentSessionContext context, Supplier<T> supplier) {
        if (context == null) {
            return supplier.get();
        }
        AgentSessionContext previous = get();
        if (previous == context) {
            return supplier.get();
        }
        set(context);
        try {
            return supplier.get();
        } finally {
            if (previous != null) {
                set(previous);
            } else {
                clear();
            }
        }
    }
}
