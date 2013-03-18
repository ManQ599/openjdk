/*
 * Copyright (c) 2010, 2013, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package jdk.nashorn.internal.codegen;

import static jdk.nashorn.internal.codegen.Condition.EQ;
import static jdk.nashorn.internal.codegen.Condition.GE;
import static jdk.nashorn.internal.codegen.Condition.GT;
import static jdk.nashorn.internal.codegen.Condition.LE;
import static jdk.nashorn.internal.codegen.Condition.LT;
import static jdk.nashorn.internal.codegen.Condition.NE;

import jdk.nashorn.internal.codegen.Label;
import jdk.nashorn.internal.codegen.types.Type;
import jdk.nashorn.internal.ir.BinaryNode;
import jdk.nashorn.internal.ir.Node;
import jdk.nashorn.internal.ir.TernaryNode;
import jdk.nashorn.internal.ir.UnaryNode;

/**
 * Branch optimizer for CodeGenerator. Given a jump condition this helper
 * class attempts to simplify the control flow
 */
final class BranchOptimizer {

    private final CodeGenerator codegen;
    private final MethodEmitter method;

    BranchOptimizer(final CodeGenerator codegen, final MethodEmitter method) {
        this.codegen = codegen;
        this.method  = method;
    }

    void execute(final Node node, final Label label, final boolean state) {
        branchOptimizer(node, label, state);
    }

    private void load(final Node node) {
        codegen.load(node);
    }

    private void branchOptimizer(final UnaryNode unaryNode, final Label label, final boolean state) {
        final Node rhs = unaryNode.rhs();

        switch (unaryNode.tokenType()) {
        case NOT:
            branchOptimizer(rhs, label, !state);
            return;
        case CONVERT:
            if (unaryNode.getType().isBoolean()) {
                branchOptimizer(rhs, label, state);
                return;
            }
            break;
        default:
            break;
        }

        // convert to boolean
        load(unaryNode);
        method.convert(Type.BOOLEAN);
        if (state) {
            method.ifne(label);
        } else {
            method.ifeq(label);
        }
    }

    private void branchOptimizer(final BinaryNode binaryNode, final Label label, final boolean state) {
        final Node lhs = binaryNode.lhs();
        final Node rhs = binaryNode.rhs();

        switch (binaryNode.tokenType()) {
        case AND:
            if (state) {
                final Label skip = new Label("skip");
                branchOptimizer(lhs, skip, false);
                branchOptimizer(rhs, label, true);
                method.label(skip);
            } else {
                branchOptimizer(lhs, label, false);
                branchOptimizer(rhs, label, false);
            }
            return;

        case OR:
            if (state) {
                branchOptimizer(lhs, label, true);
                branchOptimizer(rhs, label, true);
            } else {
                final Label skip = new Label("skip");
                branchOptimizer(lhs, skip, true);
                branchOptimizer(rhs, label, false);
                method.label(skip);
            }
            return;

        case EQ:
        case EQ_STRICT:
            assert rhs.getType().isEquivalentTo(lhs.getType()) : "type mismatch: " + lhs.getSymbol() + " to " + rhs.getSymbol();
            load(lhs);
            load(rhs);
            method.conditionalJump(state ? EQ : NE, true, label);
            return;

        case NE:
        case NE_STRICT:
            assert rhs.getType().isEquivalentTo(lhs.getType()) : "type mismatch: " + lhs.getSymbol() + " to " + rhs.getSymbol();
            load(lhs);
            load(rhs);
            method.conditionalJump(state ? NE : EQ, true, label);
            return;

        case GE:
            assert rhs.getType().isEquivalentTo(lhs.getType()) : "type mismatch: " + lhs.getSymbol() + " to " + rhs.getSymbol();
            load(lhs);
            load(rhs);
            method.conditionalJump(state ? GE : LT, !state, label);
            return;

        case GT:
            assert rhs.getType().isEquivalentTo(lhs.getType()) : "type mismatch: " + lhs.getSymbol() + " to " + rhs.getSymbol();
            load(lhs);
            load(rhs);
            method.conditionalJump(state ? GT : LE, !state, label);
            return;

        case LE:
            assert rhs.getType().isEquivalentTo(lhs.getType()) : "type mismatch: " + lhs.getSymbol() + " to " + rhs.getSymbol();
            load(lhs);
            load(rhs);
            method.conditionalJump(state ? LE : GT, state, label);
            return;

        case LT:
            assert rhs.getType().isEquivalentTo(lhs.getType()) : "type mismatch: " + lhs.getSymbol() + " to " + rhs.getSymbol() + " in " + binaryNode;
            load(lhs);
            load(rhs);
            method.conditionalJump(state ? LT : GE, state, label);
            return;

        default:
            break;
        }

        load(binaryNode);
        method.convert(Type.BOOLEAN);
        if (state) {
            method.ifne(label);
        } else {
            method.ifeq(label);
        }
    }

    private void branchOptimizer(final Node node, final Label label, final boolean state) {
        if (!(node instanceof TernaryNode)) {

            if (node instanceof BinaryNode) {
                branchOptimizer((BinaryNode)node, label, state);
                return;
            }

            if (node instanceof UnaryNode) {
                branchOptimizer((UnaryNode)node, label, state);
                return;
            }
        }

        load(node);
        method.convert(Type.BOOLEAN);
        if (state) {
            method.ifne(label);
        } else {
            method.ifeq(label);
        }
    }
}