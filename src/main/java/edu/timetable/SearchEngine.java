package edu.timetable;
import java.util.*;
import java.util.function.*;
class SearchEngine implements SearchMethod {
    static final List<String> METHODS=List.of("Hybrid","Random","Brute-force");
    private static final java.security.SecureRandom SEEDS=new java.security.SecureRandom();
    private static int previousSeed=-1;
    static synchronized int freshSeed(){
        int seed;do{seed=SEEDS.nextInt(Integer.MAX_VALUE);}while(seed==previousSeed);
        previousSeed=seed;return seed;
    }
    static final org.slf4j.Logger LOG=org.slf4j.LoggerFactory.getLogger(SearchEngine.class);
    record Settings(int seed,double seconds,int population,int generations,double mutation,long samples){
        Settings{ProblemData.require(seconds>0&&Double.isFinite(seconds)&&population>=2&&generations>0&&mutation>=0&&mutation<=1&&samples>0,"Invalid search settings");}
    }
    interface ConstraintSolver {int[] solve(SearchEngine run,int[] child,boolean optimize,double seconds);}
    final ProblemData m;final Settings settings;final Random rng;final BooleanSupplier cancelled;final long start;final LongSupplier clock;
    long evaluations,mutations,nodes,attempts;
    boolean feasibleGenerator=true,warmStart=true,reportDiagnostics=true;
    int workers=Math.min(8,Runtime.getRuntime().availableProcessors());
    int[] hint;volatile int[] best;volatile int score=Integer.MAX_VALUE;String status="Budget ended";boolean proved;
    final List<double[]> trace=new ArrayList<>();
    HybridStopping hybridStopping=HybridStopping.defaults();
    volatile boolean hybridActive;
    volatile double lastImprovementAt;
    volatile long generations,stagnantGenerations,improvements;
    SearchEngine(ProblemData model,Settings options,BooleanSupplier cancel){this(model,options,cancel,System::nanoTime);}
    SearchEngine(ProblemData model,Settings options,BooleanSupplier cancel,LongSupplier time){m=model;settings=options;rng=new Random(options.seed);cancelled=cancel;clock=time;start=clock.getAsLong();}
    double elapsed(){return (clock.getAsLong()-start)/1e9;}
    double left(){
        if(hybridActive)return switch(hybridStopping.mode()){
            case STAGNANT_GENERATIONS->Double.POSITIVE_INFINITY;
            case STAGNANT_SECONDS->hybridStopping.seconds()-(elapsed()-lastImprovementAt);
            case TIME_LIMIT->settings.seconds-elapsed();
        };
        return settings.seconds-elapsed();
    }
    boolean stop(){return cancelled.getAsBoolean()||left()<=0||hybridActive&&hybridStopping.mode()==HybridStopping.Mode.STAGNANT_GENERATIONS&&stagnantGenerations>=hybridStopping.generations();}
    double progress(){
        if(!hybridActive||hybridStopping.mode()==HybridStopping.Mode.TIME_LIMIT)return Math.min(.99,elapsed()/settings.seconds);
        return Math.min(.99,hybridStopping.mode()==HybridStopping.Mode.STAGNANT_GENERATIONS?(double)stagnantGenerations/hybridStopping.generations():(elapsed()-lastImprovementAt)/hybridStopping.seconds());
    }
    String stoppingSummary(){
        if(!hybridActive)return "";
        return " • generations "+generations+" • "+switch(hybridStopping.mode()){
            case STAGNANT_GENERATIONS->stagnantGenerations+"/"+hybridStopping.generations()+" generations without improvement";
            case STAGNANT_SECONDS->String.format(Locale.ROOT,"%.1f/%.1f seconds without improvement",elapsed()-lastImprovementAt,hybridStopping.seconds());
            case TIME_LIMIT->"fixed time limit "+settings.seconds+" seconds";
        };
    }
    int inspect(int[] genes){
        if(hybridActive&&stop())return Integer.MAX_VALUE;
        evaluations++;var result=new DefaultEvaluator().evaluate(m,new Candidate(genes));
        if(hybridActive&&stop())return Integer.MAX_VALUE;
        if(!result.feasible())return Integer.MAX_VALUE;
        if(result.objective()<score){best=genes.clone();score=result.objective();lastImprovementAt=elapsed();stagnantGenerations=0;improvements++;trace.add(new double[]{evaluations,score,elapsed()});}
        else if(hybridActive&&result.objective()==score&&rng.nextBoolean())best=genes.clone();
        return result.objective();
    }
    String scoreText(){return best==null?"searching":new SolutionScore(score,0).toString();}
    int[] sample(){int[] genes=new int[m.events.size()];for(int i=0;i<genes.length;i++)genes[i]=rng.nextInt(m.choices.get(i).size());return genes;}
    public void run(String method,ConstraintSolver cp){
        ProblemData.require(METHODS.contains(method),"Unknown search method: "+method);
        hybridActive=method.equals("Hybrid");lastImprovementAt=elapsed();
        LOG.debug("Starting {} seed={} budget={}",method,settings.seed,settings.seconds);
        if(!prepare())return;
        switch(method){
            case "Random"->{while(!stop()&&attempts<settings.samples){attempts++;int[] candidate=feasibleGenerator?greedySeed():sample();if(candidate!=null)inspect(candidate);}}
            case "Brute-force"->{int[] genes=new int[m.events.size()];Arrays.fill(genes,-1);Integer[] order=new Integer[genes.length];for(int i=0;i<order.length;i++)order[i]=i;Arrays.sort(order,Comparator.comparingInt(i->m.choices.get(i).size()));proved=enumerate(genes,order,0);}
            case "Hybrid"->hybrid(cp);
            default->throw new IllegalArgumentException(method);
        }
        finishStatus(method.equals("Brute-force"));
        LOG.debug("Finished {} status={} score={} seconds={}",method,status,score,elapsed());
    }
    private boolean prepare(){
        for(int i=0;i<m.events.size();i++)if(m.choices.get(i).isEmpty()){
            status="INFEASIBLE: "+m.diagnostics.get(i);return false;
        }
        if(reportDiagnostics)diagnostics();return true;
    }
    private void finishStatus(boolean exhaustive){
        if(cancelled.getAsBoolean())status="Cancelled";
        else if(best!=null)status=proved||(!exhaustive&&score==0)?"OPTIMAL":"FEASIBLE";
        else if(proved)status="INFEASIBLE";
        else status="UNKNOWN";
    }
    /** Internal CP oracle for model verification; CP is part of Hybrid, not a search method. */
    void solveConstraints(ConstraintSolver cp){
        hybridActive=false;if(!prepare())return;
        if(warmStart){double until=elapsed()+Math.max(0,left()*.10);while(!stop()&&elapsed()<until){int[] seed=greedySeed();if(seed!=null)inspect(seed);}hint=best==null?null:best.clone();}
        int[] answer=cp.solve(this,null,true,left());if(answer!=null)inspect(answer);finishStatus(false);
    }
    double searchSpaceLog10(){return m.choices.stream().mapToDouble(c->c.isEmpty()?Double.NEGATIVE_INFINITY:Math.log10(c.size())).sum();}
    void diagnostics(){
        var random=new Random(settings.seed());int feasible=0;
        for(int sample=0;sample<2000;sample++){int[] genes=new int[m.events.size()];for(int i=0;i<genes.length;i++)genes[i]=random.nextInt(m.choices.get(i).size());if(Evaluator.evaluate(m,genes).valid())feasible++;}
        System.out.printf(Locale.ROOT,"Search space: 10^%.3f; uniform feasible: %d/2000 (%.4f%%)%n",searchSpaceLog10(),feasible,feasible/20.0);
    }
    boolean enumerate(int[] genes,Integer[] order,int depth){
        if(stop())return false;
        if(depth==order.length){inspect(genes);return true;}
        int event=order[depth];
        for(int k=0;k<m.choices.get(event).size();k++){
            if(stop())return false;nodes++;boolean fits=true;
            for(int j=0;j<depth;j++){int previous=order[j];if(m.conflict(event,m.choices.get(event).get(k),previous,m.choices.get(previous).get(genes[previous]))!=null){fits=false;break;}}
            if(!fits)continue;genes[event]=k;
            if(!enumerate(genes,order,depth+1)){genes[event]=-1;return false;}
            genes[event]=-1;
        }
        return true;
    }
    record Member(int[] genes,SolutionScore cost){}
    Member tournament(List<Member> population){
        Member winner=population.get(rng.nextInt(population.size()));
        for(int i=1;i<3;i++){Member rival=population.get(rng.nextInt(population.size()));if(rival.cost.compareTo(winner.cost)<0)winner=rival;}
        return winner;
    }
    int[] crossover(Member a,Member b){int[] child=a.genes.clone();for(int i=0;i<child.length;i++)if(rng.nextBoolean())child[i]=b.genes[i];return child;}
    void mutate(int[] child){
        for(int i=0;i<child.length;i++)if(rng.nextDouble()<settings.mutation){
            int size=m.choices.get(i).size();if(size<2)continue;
            int replacement=rng.nextInt(size-1);if(replacement>=child[i])replacement++;
            child[i]=replacement;mutations++;
        }
    }
    int[] greedySeed(){
        int[] genes=new int[m.events.size()];Arrays.fill(genes,-1);
        var order=new ArrayList<Integer>();for(int i=0;i<genes.length;i++)order.add(i);
        Collections.shuffle(order,rng);order.sort(Comparator.comparingInt(i->m.choices.get(i).size()));
        for(int i:order){
            var options=new ArrayList<Integer>();for(int k=0;k<m.choices.get(i).size();k++)options.add(k);
            Collections.shuffle(options,rng);boolean placed=false;
            for(int k:options){
                if(stop())return null;boolean fits=true;
                for(int j=0;j<genes.length;j++)if(genes[j]>=0&&m.conflict(i,m.choices.get(i).get(k),j,m.choices.get(j).get(genes[j]))!=null){fits=false;break;}
                if(fits){genes[i]=k;placed=true;break;}
            }
            if(!placed)return null;
        }
        return Evaluator.evaluate(m,genes).valid()?genes:null;
    }
    void hybrid(ConstraintSolver cp){
        var population=new ArrayList<Member>();
        // CP calls are bounded slices, so the selected stopping rule stays responsive.
        int[] seed=cp.solve(this,null,true,Math.min(left(),5));
        if(seed!=null){inspect(seed);population.add(new Member(seed,Evaluator.evaluate(m,seed).ranking()));}
        for(int attempt=0;attempt<settings.population&&!stop();attempt++){
            int[] candidate=greedySeed();if(candidate!=null){inspect(candidate);population.add(new Member(candidate,Evaluator.evaluate(m,candidate).ranking()));}
        }
        while(!stop()){
            long revision=improvements;var next=new ArrayList<Member>();
            if(best!=null)next.add(new Member(best.clone(),new SolutionScore(score,0)));
            if(!population.isEmpty())while(next.size()<settings.population&&!stop()){
                int[] child=crossover(tournament(population),tournament(population));mutate(child);
                var cost=Evaluator.evaluate(m,child).ranking();inspect(child);
                if(cost.hard()>0&&!stop()){
                    int[] repaired=cp.solve(this,child,false,Math.min(.3,left()));
                    if(repaired!=null){child=repaired;inspect(child);cost=Evaluator.evaluate(m,child).ranking();}
                }
                next.add(new Member(child,cost));
            }
            if(!stop()&&!proved){
                // Periodic global optimization also recovers when construction found no seed.
                hint=best==null?null:best.clone();
                boolean global=generations%10==0||best==null;
                int[] improved=cp.solve(this,global?null:best.clone(),true,Math.min(global?5:1,left()));
                if(improved!=null){inspect(improved);next.add(new Member(improved,Evaluator.evaluate(m,improved).ranking()));}
            }
            if(stop())break;
            population=next;generations++;
            stagnantGenerations=improvements==revision?stagnantGenerations+1:0;
        }
    }
}
