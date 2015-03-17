/*
 * Copyright 2007 The Closure Compiler Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.javascript.jscomp;

import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;
import com.google.common.collect.Lists;
import com.google.javascript.jscomp.NodeTraversal.Callback;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;

import java.util.Collection;
import java.util.List;
import java.util.logging.Logger;
import java.util.Map;

class InlineAnnotatedMethods extends MethodCompilerPass {

  private final Supplier<String> safeNameIdSupplier;
  private final FunctionInjector injector;

  private static final Logger logger =
      Logger.getLogger(InlineAnnotatedMethods.class.getName());

  InlineAnnotatedMethods(AbstractCompiler compiler,
      Supplier<String> safeNameIdSupplier,
      boolean assumeStrictThis,
      boolean assumeMinimumCapture) {
    super(compiler);
    this.safeNameIdSupplier = safeNameIdSupplier;
    this.injector = new FunctionInjector(
        compiler, safeNameIdSupplier,
        true, assumeStrictThis, assumeMinimumCapture);
  }

  private class InlineTrivialAccessors extends InvocationsCallback {
  
    @Override
    void visit(NodeTraversal t, Node callNode, Node parent, String callName) {
      Collection<Node> definitions = methodDefinitions.get(callName);
      if (definitions == null || definitions.isEmpty()) {
        return;
      }
      
      Node firstDefinition = definitions.iterator().next();
      
      if (!NodeUtil.hasInlineAnnotation(firstDefinition)) {
        return;
      }

      if (definitions.size() == 1 || allDefinitionsEquivalent(definitions)) {
        if (injector.isDirectCallNodeReplacementPossible(firstDefinition)) {
          inlineReturnValue(callNode, firstDefinition).cloneTree();
          compiler.reportCodeChange();
        }
      } else {
        logger.fine("Method '" + callName + "' has conflicting definitions.");
      }
    }
  }

  @Override
  Callback getActingCallback() {
    return new InlineTrivialAccessors();
  }

  private Node inlineReturnValue(Node callNode, Node fnNode) {
    Node block = fnNode.getLastChild();
    Node callParentNode = callNode.getParent();

    Map<String, Node> argMap =
        FunctionArgumentInjector.getFunctionCallParameterMap(
            fnNode, callNode, this.safeNameIdSupplier);

    Node newExpression;
    if (!block.hasChildren()) {
      Node srcLocation = block;
      newExpression = NodeUtil.newUndefinedNode(srcLocation);
    } else {
      Node returnNode = block.getFirstChild();
      Preconditions.checkArgument(returnNode.isReturn());

      Node safeReturnNode = returnNode.cloneTree();
      Node inlineResult = FunctionArgumentInjector.inject(
          null, safeReturnNode, null, argMap);
      Preconditions.checkArgument(safeReturnNode == inlineResult);
      newExpression = safeReturnNode.removeFirstChild();
    }

    callParentNode.replaceChild(callNode, newExpression);
    return newExpression;
  }

  private boolean allDefinitionsEquivalent(
      Collection<Node> definitions) {
    List<Node> list = Lists.newArrayList();
    list.addAll(definitions);
    Node node0 = list.get(0);
    for (int i = 1; i < list.size(); i++) {
      if (!compiler.areNodesEqualForInlining(list.get(i), node0)) {
        return false;
      }
    }
    return true;
  }

  static final MethodCompilerPass.SignatureStore DUMMY_SIGNATURE_STORE =
      new MethodCompilerPass.SignatureStore() {
        @Override
        public void addSignature(
            String functionName, Node functionNode, String sourceFile) {
        }
  
        @Override
        public void removeSignature(String functionName) {
        }
  
        @Override
        public void reset() {
        }
      };
  
  @Override
  SignatureStore getSignatureStore() {
    return DUMMY_SIGNATURE_STORE;
  }
}
