package com.alben.createhandmade.api;

/**
 * 写操作的执行结果。
 *
 * <p>以前这些方法只返回 {@code boolean}：{@code false} 可能是"没配方"、"材料不匹配"、
 * "产物塞不下"、"过滤器挡了"，调用方无法区分，只能自己去猜或干脆重查一遍。现在每种情况
 * 有独立取值，配合 {@code simulate = true} 的预演重载，可以在真正动手前就知道会发生什么。</p>
 *
 * <p><b>约定：</b></p>
 * <ul>
 *   <li>{@link #SUCCESS} 是唯一的成功值，{@link #isSuccess()} 判断它；</li>
 *   <li>预演（{@code simulate = true}）返回 {@code SUCCESS} 表示"真跑会成功"，且**不会**有任何副作用；</li>
 *   <li>失败取值是按排查价值排序的：越具体越好，实在判不出来才用 {@link #FAILED}。</li>
 * </ul>
 */
public enum HandMadeResult {

    /** 成功。预演模式下表示"可以成功"，但什么都没改。 */
    SUCCESS,

    /** 在客户端调用，已拒绝（写操作只在服务端生效）。 */
    CLIENT_SIDE,

    /** 目标不是该工具能处理的方块/容器（例如冲压工作盆时那个位置不是工作盆）。 */
    INVALID_TARGET,

    /** 目标有效，但上面没有可加工的物品。 */
    NO_INPUT,

    /** 没有匹配的配方。 */
    NO_RECIPE,

    /** 有匹配配方，但产物被过滤器挡掉了。 */
    FILTERED_OUT,

    /** 加工能成，但产物没有空间可放（容器满、置物台占位等）。 */
    NO_SPACE,

    /** 流体不足或不匹配（灌注枪相关操作）。 */
    NO_FLUID,

    /** 缺少所需的物品/流体容器（没有 IItemHandler / IFluidHandler）。 */
    NO_HANDLER,

    /** 其它原因：内部异常、上游配方拒绝执行等。详见日志。 */
    FAILED;

    /** 是否是成功。 */
    public boolean isSuccess() {
        return this == SUCCESS;
    }

    /** 是否是"预演通过"（等价于 {@link #SUCCESS}，语义更清楚的别名）。 */
    public boolean canSucceed() {
        return this == SUCCESS;
    }
}
