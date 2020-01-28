package dong.yoogo.approval.flow.constant;

public enum FlowStatus {
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
    REJECT,
    /*等待落地复核*/
    WAIT_FOR_LANDING_REVIEW,
    /**
     * 交易成功
     */
    TRANSACTION_SUCCESS,
    /**
     * 交易部分成功
     */
    TRANSACTION_PARTIAL_SUCCESS,
    /**
     * 交易失败
     */
    TRANSACTION_FAIL,
    /**
     * 业务已受理,往往是状态未知,多为跨行转账
     */
    TRANSACTION_INPROCESS

}
