package dong.yoogo.approval.process.domain.constant;

public enum ProcessStatus {
    /**
     * 等待复核
     */
    WAIT_FOR_REVIEW,
    /**
     * 正处于多级复核流程中
     */
    IN_PROCESS,
    /**
     * 复核流程完成,但是还没有触发交易
     */
    COMPLETED,
    /**
     * 驳回
     */
    REJECT
}
