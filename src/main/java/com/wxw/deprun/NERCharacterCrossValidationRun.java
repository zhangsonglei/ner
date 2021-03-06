package com.wxw.deprun;

import java.io.File;
import java.io.IOException;

import com.wxw.character.NERCharacterContextGenerator;
import com.wxw.character.NERCharacterContextGeneratorConf;
import com.wxw.character.NERCharacterEvaluator;
import com.wxw.character.NERCharacterME;
import com.wxw.character.NERCharacterModel;
import com.wxw.character.NERCharacterSampleStream;
import com.wxw.evaluate.NEREvaluateMonitor;
import com.wxw.evaluate.NERMeasure;
import com.wxw.sample.FileInputStreamFactory;
import com.wxw.sample.NERWordOrCharacterSample;

import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.PlainTextByLineStream;
import opennlp.tools.util.TrainingParameters;
import opennlp.tools.util.eval.CrossValidationPartitioner;

public class NERCharacterCrossValidationRun {

	private final String languageCode;

    private final TrainingParameters params;

    private NEREvaluateMonitor[] listeners;
    
    public NERCharacterCrossValidationRun(String languageCode,TrainingParameters trainParam,NEREvaluateMonitor... listeners){
    	this.languageCode = languageCode;
        this.params = trainParam;
        this.listeners = listeners;
    }
    
    public void evaluate(ObjectStream<NERWordOrCharacterSample> samples, int nFolds, NERCharacterContextGenerator contextGen) throws IOException{
    	CrossValidationPartitioner<NERWordOrCharacterSample> partitioner = new CrossValidationPartitioner<NERWordOrCharacterSample>(samples, nFolds);
		int run = 1;
		//小于折数的时候
		while(partitioner.hasNext()){
			System.out.println("Run"+run+"...");
			CrossValidationPartitioner.TrainingSampleStream<NERWordOrCharacterSample> trainingSampleStream = partitioner.next();
			
			//训练模型
			trainingSampleStream.reset();
			NERCharacterModel model = NERCharacterME.train(languageCode, trainingSampleStream, params, contextGen);

			NERCharacterEvaluator evaluator = new NERCharacterEvaluator(new NERCharacterME(model, contextGen), listeners);
			NERMeasure measure = new NERMeasure();
			
			evaluator.setMeasure(measure);
	        //设置测试集（在测试集上进行评价）
	        evaluator.evaluate(trainingSampleStream.getTestSampleStream());
	        
	        System.out.println(measure);
	        run++;
		}
    }
    
    private static void usage(){
    	System.out.println(NERCharacterCrossValidationRun.class.getName() + " -data <corpusFile> -encoding <encoding> " + "[-cutoff <num>] [-iters <num>] [-folds <nFolds>] ");
    }
    
    public static void main(String[] args) throws IOException {
    	if (args.length < 1)
        {
            usage();
            return;
        }

        int cutoff = 3;
        int iters = 100;
        int folds = 10;
        File corpusFile = null;
        String encoding = "UTF-8";
        for (int i = 0; i < args.length; i++)
        {
            if (args[i].equals("-data"))
            {
                corpusFile = new File(args[i + 1]);
                i++;
            }
            else if (args[i].equals("-encoding"))
            {
                encoding = args[i + 1];
                i++;
            }
            else if (args[i].equals("-cutoff"))
            {
                cutoff = Integer.parseInt(args[i + 1]);
                i++;
            }
            else if (args[i].equals("-iters"))
            {
                iters = Integer.parseInt(args[i + 1]);
                i++;
            }
            else if (args[i].equals("-folds"))
            {
                folds = Integer.parseInt(args[i + 1]);
                i++;
            }
        }

        TrainingParameters params = TrainingParameters.defaultParams();
        params.put(TrainingParameters.CUTOFF_PARAM, Integer.toString(cutoff));
        params.put(TrainingParameters.ITERATIONS_PARAM, Integer.toString(iters));
        
        NERCharacterContextGenerator context = new NERCharacterContextGeneratorConf();
        System.out.println(context);
        ObjectStream<String> lineStream = new PlainTextByLineStream(new FileInputStreamFactory(corpusFile), encoding);       
        ObjectStream<NERWordOrCharacterSample> sampleStream = new NERCharacterSampleStream(lineStream);
        NERCharacterCrossValidationRun run = new NERCharacterCrossValidationRun("zh",params);
        run.evaluate(sampleStream,folds,context);
	}
}
