package com.alibaba.cloud.ai.manus.config.rpc;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
/**
 * 因为是异步调用，所以不能用ThreadLocal，用ThreadLocal会丢失数据，这边是基于 rootPlanID 进行数据隔离
 */
public class AuthContext {
    private static final ThreadLocal<String> CONTEXT_PLAN_ID = new ThreadLocal<>();
    private static final ApproxLruMap<String,String> TOKEN_HOLDER = new ApproxLruMap<>(500);

    public static void setContextPlanId(String rootPlanId) {
        CONTEXT_PLAN_ID.set(rootPlanId);
    }
    public static void setToken(String rootPlanId,String token) {
        TOKEN_HOLDER.put(rootPlanId,token);
    }

    public static String getToken(String rootPlanId) {
        return TOKEN_HOLDER.get(rootPlanId);
    }
    public static String getToken() {
        String rootPlanId = CONTEXT_PLAN_ID.get();
        return TOKEN_HOLDER.get(rootPlanId);
    }

    public static class ApproxLruMap<K, V> {
        private final ConcurrentHashMap<K, V> map = new ConcurrentHashMap<>();
        private final ConcurrentLinkedQueue<K> queue = new ConcurrentLinkedQueue<>();
        private final int maxSize;
        private final AtomicInteger count = new AtomicInteger(0);

        public ApproxLruMap(int maxSize) { this.maxSize = maxSize; }

        public V put(K key, V value) {
            V prev = map.put(key, value);
            if (prev == null) {
                int c = count.incrementAndGet();
                queue.add(key);
                // 当超过容量，尝试移除队头（可能已被其他线程移除）
                while (c > maxSize) {
                    K eldest = queue.poll();
                    if (eldest == null) break;
                    V removed = map.remove(eldest);
                    if (removed != null) c = count.decrementAndGet();
                }
            } else {
                // 覆盖时也可以把 key 加到队尾（可选），以近似更新其“最近使用”
                queue.add(key);
            }
            return prev;
        }

        public V get(K key) {
            return map.get(key);
        }
    }
}
