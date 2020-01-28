package dong.yoogo.approval.flow.constant;

public interface ErrorCode {

    String HEAD = "EUP.APR.";

    /**
     * 交易不存在,指定的流程不存在
     */
    String TRANSACTION_NOT_EXISTS = "B01002";
    /**制单员自己复核*/
    String SELF_APPROVAL = "B01003";
    /**不是授权的复核员*/
    String NOT_ALLOWED = "B01004";
    /**流程已经结束*/
    String FLOW_ENDED = "B01005";

    /**
     * 余额不足
     */
    String BALANCE_INSUFFICIENT = "B01007";

    String FAIL_TO_NOTIFY_TRANSACTION = "B01008";
    
    /**
     * 制单不存在
     */
    String ORDER_NOT_EXISTS = "B01009"; 
    
    /**
     * 银行系统升级中(功能关闭)
     */
    String FUNCTION_CLOSE = "B01010";
    
    
    /**
     * [%]字段信息不能为空
     */
    String PARAMETER_NOT_NULL = "B01011";
    
    /**
     * 该笔交易已提交，请勿重复操作
     */
    String ALREADY_SUBMIT = "B01012";
    
    /**
     * [%s]信息异常
     */
    String PARAMETER_ERROR = "B01013";
}
