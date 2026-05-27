package com.sumo.agent.agent.loop;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RunTrace 单元测试 — 验证 append / recent / last 行为，覆盖空 trace 和越界参数。
 */
class RunTraceTest {

    // 1. 空 trace recent(3) 返回空 List
    @Test
    void recent_emptyTrace_returnsEmptyList() {
        RunTrace trace = new RunTrace();

        List<TraceEntry> result = trace.recent(3);

        assertNotNull(result, "recent 不应返回 null");
        assertTrue(result.isEmpty(), "空 trace 应返回空列表");
    }

    // 2. 5 条 → recent(3) 返回最后 3 条，顺序保持
    @Test
    void recent_fiveEntriesTakeThree_returnsLastThreeInOrder() {
        RunTrace trace = new RunTrace();
        for (int i = 1; i <= 5; i++) {
            trace.append(makeEntry(i));
        }

        List<TraceEntry> result = trace.recent(3);

        assertEquals(3, result.size());
        assertEquals(3, result.get(0).getIteration(), "首项应为第 3 轮");
        assertEquals(4, result.get(1).getIteration(), "次项应为第 4 轮");
        assertEquals(5, result.get(2).getIteration(), "末项应为第 5 轮");
    }

    // 3. 2 条 → recent(3) 返回全部 2 条
    @Test
    void recent_lessThanN_returnsAll() {
        RunTrace trace = new RunTrace();
        trace.append(makeEntry(1));
        trace.append(makeEntry(2));

        List<TraceEntry> result = trace.recent(3);

        assertEquals(2, result.size(), "不足 N 条时返回全部");
        assertEquals(1, result.get(0).getIteration());
        assertEquals(2, result.get(1).getIteration());
    }

    // 4. recent(0) / recent(-1) → 空 List
    @Test
    void recent_zeroOrNegative_returnsEmpty() {
        RunTrace trace = new RunTrace();
        trace.append(makeEntry(1));
        trace.append(makeEntry(2));

        assertTrue(trace.recent(0).isEmpty(), "recent(0) 应返回空");
        assertTrue(trace.recent(-1).isEmpty(), "recent(-1) 应返回空");
    }

    // 5. last() 在空 trace 返回 null
    @Test
    void last_emptyTrace_returnsNull() {
        RunTrace trace = new RunTrace();

        assertNull(trace.last(), "空 trace last() 必须返回 null");
    }

    // 6. last() 返回最近追加的那条
    @Test
    void last_returnsMostRecentlyAppended() {
        RunTrace trace = new RunTrace();
        TraceEntry first = makeEntry(1);
        TraceEntry second = makeEntry(2);
        TraceEntry third = makeEntry(3);
        trace.append(first);
        trace.append(second);
        trace.append(third);

        assertSame(third, trace.last(), "last 应返回最后追加的实例");
    }

    // 辅助：构造 iteration 标记的 TraceEntry，其它字段无关紧要
    private TraceEntry makeEntry(int iteration) {
        TraceEntry entry = new TraceEntry();
        entry.setIteration(iteration);
        entry.setScoreBefore(0);
        entry.setScoreAfter(0);
        entry.setIssueCount(0);
        entry.setResponseLength(0);
        entry.setGameVersion(0);
        entry.setSummary("iter " + iteration);
        return entry;
    }
}
