package dong.yoogo.approval.flow.domain;

import java.util.List;

import lombok.Data;


@Data
public class CancelCustomerModelFlowDTO {
	/**
	 * 客户号
	 */
	private Long customerNo;	

	/**
	 * 客户渠道Id列表
	 * 如果为空，删除所以渠道正在处理的交易;如果不空，只删除相关渠道的正在处理的交易。
	 */
	private List<String> channelIds;
	
}
