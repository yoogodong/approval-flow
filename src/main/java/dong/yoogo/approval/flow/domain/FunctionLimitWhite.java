/**
 * 
 */
package dong.yoogo.approval.flow.domain;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

/**
 * @author songsongtao
 *
 */
@Entity
@Table(name = "TB_OC_APR_FUN_LIMIT_WHITE")
@EqualsAndHashCode(of = "functionCode")
@Getter
@Setter
public class FunctionLimitWhite {
	
	@Id
	@Column(name = "FUNCTION_CODE")
	private String functionCode;
	
	/**
	 * 备注
	 */
	@Column(name = "REMARK")
	private String remark;
}
