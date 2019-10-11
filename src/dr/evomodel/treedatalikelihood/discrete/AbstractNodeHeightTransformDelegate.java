/*
 * AbstractNodeHeightTransformDelegate.java
 *
 * Copyright (c) 2002-2017 Alexei Drummond, Andrew Rambaut and Marc Suchard
 *
 * This file is part of BEAST.
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership and licensing.
 *
 * BEAST is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 *  BEAST is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with BEAST; if not, write to the
 * Free Software Foundation, Inc., 51 Franklin St, Fifth Floor,
 * Boston, MA  02110-1301  USA
 */

package dr.evomodel.treedatalikelihood.discrete;

import dr.evomodel.tree.TreeModel;
import dr.evomodel.tree.TreeParameterModel;
import dr.evomodelxml.continuous.hmc.NodeHeightTransformParser;
import dr.inference.model.AbstractModel;
import dr.inference.model.Parameter;

/**
 * @author Marc A. Suchard
 * @author Xiang Ji
 */
public abstract class AbstractNodeHeightTransformDelegate extends AbstractModel {
    //TODO: remove this class when finished with everything
    protected TreeModel tree;
    protected Parameter nodeHeights;
    protected TreeParameterModel indexHelper;

    public AbstractNodeHeightTransformDelegate(TreeModel treeModel,
                                               Parameter nodeHeights) {
        super(NodeHeightTransformParser.NAME);
        this.tree = treeModel;
        this.nodeHeights = new NodeHeightParameter("internalNodeHeights", tree, false);
        indexHelper = new TreeParameterModel(treeModel, new Parameter.Default(tree.getNodeCount() - 1), false);
        addVariable(nodeHeights);
    }

    public void setNodeHeights(double[] nodeHeights) {
        if (nodeHeights.length != this.nodeHeights.getDimension()) {
            throw new RuntimeException("Dimension mismatch!");
        }

        for (int i = 0; i < nodeHeights.length; i++) {
            this.nodeHeights.setParameterValueQuietly(i, nodeHeights[i]);
        }
        tree.pushTreeChangedEvent();
    }

    public static class NodeHeightParameter extends Parameter.Proxy {

        private TreeModel tree;
        private TreeParameterModel indexHelper;

        public NodeHeightParameter(String name,
                                   TreeModel tree,
                                   boolean includeRoot) {
            super(name, includeRoot ? tree.getInternalNodeCount() : tree.getInternalNodeCount() - 1);
            this.tree = tree;
            this.indexHelper = new TreeParameterModel(tree,
                    new Parameter.Default(includeRoot ? tree.getInternalNodeCount() : tree.getInternalNodeCount() - 1, 0.0),
                    includeRoot);
        }

        private int getNodeNumber(int index) {
            return indexHelper.getNodeNumberFromParameterIndex(index) + tree.getExternalNodeCount();
        }

        @Override
        public double getParameterValue(int dim) {
            return tree.getNodeHeight(tree.getNode(getNodeNumber(dim)));
        }

        @Override
        public void setParameterValue(int dim, double value) {
            tree.setNodeHeight(tree.getNode(getNodeNumber(dim)), value);
        }

        @Override
        public void setParameterValueQuietly(int dim, double value) {
            tree.setNodeHeightQuietly(tree.getNode(getNodeNumber(dim)), value);
        }

        @Override
        public void setParameterValueNotifyChangedAll(int dim, double value) {
            setParameterValue(dim, value);
        }
    }

    public Parameter getNodeHeights() {
        return nodeHeights;
    }

    @Override
    protected void storeState() {

    }

    @Override
    protected void restoreState() {

    }

    @Override
    protected void acceptState() {

    }

    abstract double[] transform(double[] values);

    abstract double[] inverse(double[] values);

    abstract String getReport();

    abstract Parameter getParameter();

    abstract double getLogJacobian(double[] values);

    abstract double[] updateGradientLogDensity(double[] gradient, double[] value);

    abstract double[] updateGradientUnWeightedLogDensity(double[] gradient, double[] value, int from, int to);

}