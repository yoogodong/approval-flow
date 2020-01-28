package dong.yoogo.approval.flow.domain;

import java.util.List;

import lombok.Data;

@Data
public class UserModelFlowDTO {
	/**
	 * 用户号
	 */
	private Long userNo;	

	/**
	 * 用户渠道Id列表
	 * 如果为空，删除用户所以渠道正在处理的交易;如果不空，只删除相关渠道的正在处理的交易。
	 */
	private List<String> channelIds;
	
	
}
