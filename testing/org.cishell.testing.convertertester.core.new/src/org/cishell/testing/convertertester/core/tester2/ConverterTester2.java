package org.cishell.testing.convertertester.core.tester2;

import java.io.File;
import java.util.ArrayList;
import java.util.Dictionary;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.cishell.framework.CIShellContext;
import org.cishell.framework.algorithm.AlgorithmProperty;
import org.cishell.framework.data.BasicData;
import org.cishell.framework.data.Data;
import org.cishell.framework.data.DataProperty;
import org.cishell.testing.convertertester.core.converter.graph.ConverterGraph;
import org.cishell.testing.convertertester.core.converter.graph.ConverterPath;
import org.cishell.testing.convertertester.core.tester2.graphcomparison.IdsNotPreservedComparer;
import org.cishell.testing.convertertester.core.tester2.graphcomparison.IdsPreservedComparer;
import org.cishell.testing.convertertester.core.tester2.graphcomparison.LossyComparer;
import org.cishell.testing.convertertester.core.tester2.graphcomparison.NewGraphComparer;
import org.cishell.testing.convertertester.core.tester2.reportgen.ReportGenerator;
import org.cishell.testing.convertertester.core.tester2.reportgen.results.AllTestsResult;
import org.cishell.testing.convertertester.core.tester2.reportgen.results.FilePassResult;
import org.cishell.testing.convertertester.core.tester2.reportgen.results.TestResult;
import org.osgi.framework.BundleContext;
import org.osgi.service.log.LogService;

/**
 * @author mwlinnem
 *
 */
public class ConverterTester2 implements AlgorithmProperty {

	private LogService log;
	
	private TestFileKeeper testFileKeeper;
	private TestRunner testRunner;
	
	public ConverterTester2(LogService log) {
		this.log = log;
		this.testFileKeeper = 
			new TestFileKeeper(TestFileKeeper.DEFAULT_ROOT_DIR, log);
		this.testRunner = new DefaultTestRunner(log);
	}
	
	/**
	 * Tests the provided converters, and passes the results of those tests to
	 * the report generators. Report Generators are side-effected, which takes
	 * the place of a return value.
	 * @param reportGenerators process the test results.
	 * @param cContext the CIShell Context
	 * @param bContext the Bundle Context
	 */
	public void execute(
			ConverterGraph converterGraph,
			ReportGenerator[] reportGenerators,
			CIShellContext cContext,
			BundleContext bContext) {
		
		//run the tests
		
		TestResult[] rawResults = 
			runAllTests(converterGraph, cContext, bContext);
		AllTestsResult allTestsResult = new AllTestsResult(rawResults);
		
		//feed test results to the report generators
		
		for (int ii = 0; ii < reportGenerators.length; ii++) {
			ReportGenerator reportGenerator = reportGenerators[ii];
			
			reportGenerator.generateReport(allTestsResult);
		}
	}
	
	public TestResult[] runAllTests(ConverterGraph convGraph,
			CIShellContext cContext, BundleContext bContext) {
		
		
		
		Map fileFormatToTestConvs = convGraph.getTestMap();
		Map fileFormatToCompareConvs = convGraph.getCompareMap();
		
		List testResults = new ArrayList();
		
		Set fileFormats = fileFormatToTestConvs.keySet();
		
		/*
		 * for each file format, get the corresponding test converter paths
		 * and comparison converter path.
		 */
		
		Iterator iter = fileFormats.iterator();
		while(iter.hasNext()) {
			String fileFormat = (String) iter.next();
			
			ArrayList testConvList = 
				(ArrayList) fileFormatToTestConvs.get(fileFormat);
			
			ConverterPath[] testConvs  =
				(ConverterPath[]) testConvList.toArray(new ConverterPath[0]);
			
			ConverterPath compareConv = 
				(ConverterPath) fileFormatToCompareConvs.get(fileFormat); 
			
			/*
			 * For each test converter, use that test converter and
			 * the corresponding comparison converter to run a test.
			 */
			
			for (int kk = 0; kk < testConvs.length; kk++) {
				ConverterPath testConv = testConvs[kk];
				
				TestResult testResult = 
					runATest(testConv, compareConv, cContext, bContext);
				
				if (testResult != null) {
					testResults.add(testResult);
				}
			}
		}
		
		return (TestResult[]) testResults.toArray(new TestResult[0]);
	}
	
	
	private TestResult runATest(ConverterPath testConvs,
			ConverterPath compareConvs, CIShellContext cContext,
			BundleContext bContext) {
		
		//get test file data corresponding to the format these converters accept.
		
		String fileFormat = testConvs.getAcceptedFileFormat();
		String[] testFilePaths = testFileKeeper.getTestFilePaths(fileFormat);
		Data[][] testFileData = wrapInData(testFilePaths, fileFormat);
		
		//determine how we will compare the graphs
		
		boolean isLossy = testConvs.isLossy() && compareConvs.isLossy();
		boolean preserveIDs = testConvs.preservesIDs() &&
			compareConvs.preservesIDs();
		
		NewGraphComparer comparer = getComparer(isLossy, preserveIDs);
        
		//pack all the data relevant to the test into a single object.
        TestConfigData testBasicData = new TestConfigData(comparer, testConvs,
        		compareConvs, cContext, testFileData);
        
        //run the test
        FilePassResult[] results = this.testRunner.runTest(testBasicData);     
        
        //return the results of the test
        return new TestResult(results, testConvs, compareConvs);    
	}
		
	private Data[][] wrapInData(String[] testFilePaths, String format) {
		
		Data[][] results = new Data[testFilePaths.length][1];
		for (int ii = 0; ii < testFilePaths.length; ii++) {
			String filePath = testFilePaths[ii];
			
			results[ii] = 
				new Data[] {new BasicData(new File(filePath), format)};
			
			Dictionary metadata = results[ii][0].getMetaData();
			metadata.put(DataProperty.LABEL, filePath);
		}
		
		return results;
	}
	
	private NewGraphComparer getComparer(boolean areLossy,
			boolean idsPreserved) {
		
		if (areLossy) {
			return new LossyComparer();
		} else if (! idsPreserved) {
			return new IdsNotPreservedComparer();
		} else {
			return new IdsPreservedComparer();
		}
	}
	
}
