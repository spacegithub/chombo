/*
 * chombo: Hadoop Map Reduce utility
 * Author: Pranab Ghosh
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you
 * may not use this file except in compliance with the License. You may
 * obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0 
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package org.chombo.rules;

import org.chombo.util.BaseAttribute;

/**
 * @author pranab
 *
 */
public class RuleOperator extends Operator {

	/**
	 * @param root
	 * @param parent
	 * @param token
	 */
	public RuleOperator(Expression root, Expression parent, String token) {
		super(root,  parent,  token);
	}

	@Override
	public Object evaluate() {
		if (children.size() != 1 || children.size() != 2) {
			throw new IllegalStateException("rule operator has invalid number of operands " + children.size());
		}
		value = null;
		
		if (children.size() == 2) {
			//if then rule
			Expression left = children.get(0);
			Expression right = children.get(1);
			Object leftVal = left.evaluate();
			Object rightVal = right.evaluate();
			
			if (left.type.equals(BaseAttribute.DATA_TYPE_BOOLEAN) && right.type.equals(BaseAttribute.DATA_TYPE_BOOLEAN)) {
				boolean ifVal = (Boolean)leftVal;
				boolean thenVal = (Boolean)rightVal;
				if (ifVal) {
					value = thenVal;
				} else {
					value = Boolean.TRUE;
				}
			}
			if (null == value) {
				throw new IllegalStateException("failed evaluation for rule operator " + left.type + "  " + right.type);
			}
		} else if (children.size() == 1) {
			//just expression
			Expression child = children.get(0);
			Object childVal = child.evaluate();
			if (child.type.equals(BaseAttribute.DATA_TYPE_BOOLEAN)) {
				value = childVal;
			}
			if (null == value) {
				throw new IllegalStateException("failed evaluation for rule operator " + child.type );
			}
		}
		
		return value;
	}

	@Override
	public int getPrecedence() {
		return this.RULE_PREC;
	}
	
}
