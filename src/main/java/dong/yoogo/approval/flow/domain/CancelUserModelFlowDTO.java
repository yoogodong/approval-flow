package dong.yoogo.approval.flow.domain;

import java.util.List;

import lombok.Data;


@Data
public class CancelUserModelFlowDTO {
	/**
	 * 客户号
	 */
	private Long customerNo;
	
	/**
	 * 用户(操作员)号列表 
	 */
	private List<UserModelFlowDTO> users;
	
}
