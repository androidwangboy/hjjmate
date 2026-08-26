package vip.mate.agent.context;

/** Subscription-time marker; callers capture it before asynchronous lifecycle callbacks. */
public final class GoalContinuationContext {
    private static final ThreadLocal<Boolean> ACTIVE = new ThreadLocal<>();
    private GoalContinuationContext() {}
    public static boolean active() { return Boolean.TRUE.equals(ACTIVE.get()); }
    public static <T> T call(java.util.function.Supplier<T> action) {
        Boolean previous=ACTIVE.get();
        ACTIVE.set(true);
        try { return action.get(); }
        finally { if(previous==null) ACTIVE.remove(); else ACTIVE.set(previous); }
    }
}
