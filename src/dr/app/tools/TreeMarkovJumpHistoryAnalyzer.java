/*
 * TaxonMarkovJumpHistory.java
 *
 * Copyright (c) 2002-2020 Alexei Drummond, Andrew Rambaut and Marc Suchard
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

package dr.app.tools;

import dr.app.beast.BeastVersion;
import dr.app.util.Arguments;
import dr.evolution.tree.NodeRef;
import dr.evolution.tree.Tree;
import dr.evolution.tree.TreeUtils;
import dr.evolution.util.Taxon;
import dr.util.Version;

import java.io.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author Philippe Lemey
 * @author Marc Suchard
 */
public class TreeMarkovJumpHistoryAnalyzer extends BaseTreeTool {

    private final static Version version = new BeastVersion();

    private static final String HISTORY = "history";
    private static final String BURN_IN = "burnIn";
    private static final String[] falseTrue = {"false", "true"};
    private static final String NAME_CONTENT = "nameContent";

//    private static final boolean NEW_OUTPUT = true;

    private TreeMarkovJumpHistoryAnalyzer(String inputFileName,
                                          String outputFileName,
                                          int burnIn,
                                          String[] taxaToIgnore,
                                          boolean basedOnNameContent,
                                          double mrsd
     ) throws IOException {

        List<Tree> trees = new ArrayList<>();

        readTrees(trees, inputFileName, burnIn);

        Set taxa = getTaxaToIgnore(trees.get(0), taxaToIgnore, basedOnNameContent);

        this.mrsd = mrsd;

        this.ps = openOutputFile(outputFileName);
        processTrees(trees, burnIn, taxa);
        closeOutputFile(ps);
    }

    private Set getTaxaToIgnore(Tree tree, String[] taxaToProcess, boolean basedOnContent) {
        Set taxa = new HashSet();
        if (taxaToProcess != null) {
            for (String name : taxaToProcess) {
                if(!basedOnContent) {
                    int taxonId = tree.getTaxonIndex(name);
                    if (taxonId == -1) {
                        throw new RuntimeException("Unable to find taxon '" + name + "'.");
                    }
                    taxa.add(name);
                }else {
                    int counter = 0;
                    for(int i = 0; i < tree.getTaxonCount(); i++) {
                        Taxon taxon = tree.getTaxon(i);
                        String taxonName = taxon.toString();
                        if (taxonName.contains(name)) {
                            taxa.add(taxon);
                            counter ++;
                        }
                    }
                    if (counter == 0){
                        throw new RuntimeException("Unable to find taxon with a name containing '" + name + "'.");
                    }
                }
            }
        }
        return taxa;
    }

    private void processTrees(List<Tree> trees, int burnIn, Set taxa) {
        if (burnIn < 0) {
            burnIn = 0;
        }
        for (int i = burnIn; i < trees.size(); ++i) {
            Tree tree = trees.get(i);
            processOneTree(tree, taxa);
        }
    }

    private void processOneTree(Tree tree, Set taxa) {

        String treeId = tree.getId();
        if (treeId.startsWith("STATE_")) {
            treeId = treeId.replaceFirst("STATE_", "");
        }
//        System.out.println(treeId);

        for (int i = 0; i < tree.getNodeCount(); ++i) {
            NodeRef node = tree.getNode(i);
            if (nodeToConsider(tree, node, taxa)){
                Object[] jumps = readCJH(node, tree);
                if (jumps != null) {
                    for (int j = jumps.length - 1; j >= 0; j--) {
                        Object[] jump = (Object[]) jumps[j];
                        Row row = new Row(treeId, (String) jump[1], (String) jump[2], adjust((Double) jump[0]));
                        ps.println(row);
                    }
                }
            }
        }
    }

    private boolean nodeToConsider(Tree tree, NodeRef node, Set taxa){
        boolean consider = true;
        Set descendants = TreeUtils.getDescendantLeaves(tree,node);
        descendants.removeAll(taxa);
        if (descendants.isEmpty()){
            consider = false;
        }
        return consider;
    }

    private class Row {
        String treeId;
        String startLocation;
        String endLocation;
        double time;

        private static final String DELIMITER = ",";

        private Row(String treeId,
                    String startLocation, String endLocation,
                            double time) {
            this.treeId = treeId;
            this.startLocation = startLocation; this.endLocation = endLocation;
            this.time = time;
        }

        public String toString() {
            return treeId + DELIMITER
                    + startLocation + DELIMITER + endLocation + DELIMITER
                    + time;
        }
    }


    private double adjust(double time) {
        if (mrsd < Double.MAX_VALUE) {
            time = mrsd - time;
        }
        return time;
    }


    private static Object[] readCJH(NodeRef node, Tree treeTime) {
        if (treeTime.getNodeAttribute(node, HISTORY) != null) {
            return (Object[]) treeTime.getNodeAttribute(node, HISTORY);
        } else {
            return null;
        }
    }

    protected PrintStream openOutputFile(String outputFileName) {

        PrintStream ps = super.openOutputFile(outputFileName);
        ps.println("treeId,startLocation,endLocation,time");
        return ps;
    }

    private void closeOutputFile(PrintStream ps) {
        if (ps != null) {
            ps.close();
        }
    }

    private double mrsd;

    private PrintStream ps;

    public static void printTitle() {
        progressStream.println();
        centreLine("TaxonMarkovJumpHistory " + version.getVersionString() + ", " + version.getDateString(), 60);
        centreLine("tool to get a Markov Jump History for a Taxon", 60);
        centreLine("by", 60);
        centreLine("Philippe Lemey and Marc Suchard", 60);
        progressStream.println();
        progressStream.println();
    }

    public static void printUsage(Arguments arguments) {

        arguments.printUsage("TaxonMarkovJumpHistory", "<input-file-name> [<output-file-name>]");
        progressStream.println();
        progressStream.println("  Example: taxonMarkovJumpHistory -taxaToProcess taxon1,taxon2 input.trees output.trees");
        progressStream.println();
    }

    //Main method
    public static void main(String[] args) throws IOException {

        String[] taxaToIgnore = null;
        double mrsd = Double.MAX_VALUE;
        int burnIn = -1;
        boolean basedOnNameContent = false;

        printTitle();

        Arguments arguments = new Arguments(
                new Arguments.Option[]{
                        new Arguments.IntegerOption(BURN_IN, "the number of states to be considered as 'burn-in' [default = 0]"),
                        new Arguments.StringOption("taxaToIgnore", "list", "a list of taxon names that defines parts of trees to ignore in MJH processing"),
                        new Arguments.RealOption("mrsd", "The most recent sampling time to convert heights to times [default=MAX_VALUE]"),
                        new Arguments.StringOption(NAME_CONTENT, falseTrue, false,
                                "add true noise [default = true])"),
                        new Arguments.Option("help", "option to print this message"),
                });


        handleHelp(arguments, args, TaxaMarkovJumpHistoryAnalyzer::printUsage);

        if (arguments.hasOption("taxaToIgnore")) {
            taxaToIgnore = Branch2dRateToGrid.parseVariableLengthStringArray(arguments.getStringOption("taxaToIgnore"));
        }


        if (arguments.hasOption("mrsd")) {
            mrsd = arguments.getRealOption("mrsd");
        }

        if (arguments.hasOption(BURN_IN)) {
            burnIn = arguments.getIntegerOption(BURN_IN);
            System.err.println("Ignoring a burn-in of " + burnIn + " trees.");
        }

        String nameContentString = arguments.getStringOption(NAME_CONTENT);
        if (nameContentString != null && nameContentString.compareToIgnoreCase("true") == 0)
            basedOnNameContent = true;

        String[] fileNames = getInputOutputFileNames(arguments, TaxaMarkovJumpHistoryAnalyzer::printUsage);

        new TreeMarkovJumpHistoryAnalyzer(fileNames[0], fileNames[1], burnIn,
                taxaToIgnore,
                basedOnNameContent,
                mrsd
        );

        System.exit(0);
    }
}