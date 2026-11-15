package edu.timetable;

import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;

class HybridStoppingTest {
    static final HybridStopping.Mode GENS=HybridStopping.Mode.STAGNANT_GENERATIONS,
        IDLE=HybridStopping.Mode.STAGNANT_SECONDS,TIME=HybridStopping.Mode.TIME_LIMIT;
    ProblemData model(boolean pinned){
        var p=SimpleProject.tinyDemo();p.subjects=new ArrayList<>(p.subjects.subList(0,1));
        p.optimizationGoal=ScheduleObjective.Goal.EARLIER_FINISH;
        p.days.set(0,new SimpleProject.Day("Sunday",true,540,780,30,List.of()));
        if(pinned)p.pin(new SimpleProject.Placement("tiny-0",1,"Sunday",540,"Tiny room"));
        return new ProblemData(p.toData());
    }
    int choice(ProblemData m,int start){
        for(int k=0;k<m.choices.getFirst().size();k++)if(m.choices.getFirst().get(k).start()==start)return k;
        throw new AssertionError("Missing start");
    }
    SearchEngine run(HybridStopping rule,double runtime,boolean improve){
        var m=model(!improve);var simulatedTime=new AtomicLong();int seed=improve?choice(m,720):0;
        var search=new SearchEngine(m,new SearchEngine.Settings(7,runtime,2,1,0,1),()->false,simulatedTime::get){
            int changes;
            int[] greedySeed(){return new int[]{seed};}
            void mutate(int[] child){
                changes++;simulatedTime.addAndGet(improve?500_000_000L:100_000_000L);
                child[0]=improve?choice(m,changes==3?660:changes==6?600:720):0;
            }
        };
        search.hybridStopping=rule;search.reportDiagnostics=false;
        search.run("Hybrid",(engine,child,optimize,seconds)->{
            if(simulatedTime.get()==0)simulatedTime.set(1_000_000_000L);
            engine.proved=!improve;
            return new int[]{seed};
        });
        assertNotNull(search.best);assertTrue(Evaluator.evaluate(m,search.best).valid());return search;
    }
    @Test void defaultsAndInvalidValues(){
        assertEquals(new HybridStopping(GENS,1000,15),HybridStopping.defaults());
        assertThrows(IllegalArgumentException.class,()->new HybridStopping(null,1,1));
        assertThrows(IllegalArgumentException.class,()->new HybridStopping(GENS,0,1));
        assertThrows(IllegalArgumentException.class,()->new HybridStopping(IDLE,1,Double.NaN));
        assertThrows(IllegalArgumentException.class,()->new HybridStopping(IDLE,1,0));
    }
    @Test void countsOneThousandConsecutiveGenerationsWithoutTheOldGenerationOrTimeCaps(){
        var search=run(HybridStopping.defaults(),.001,false);
        assertEquals(1000,search.generations);assertEquals(1000,search.stagnantGenerations);
        assertTrue(search.elapsed()>100);assertEquals(0,search.score);
    }
    @Test void betterScoresResetTheGenerationCounterButEqualOrWorseScoresDoNot(){
        var search=run(new HybridStopping(GENS,4,15),.001,true);
        assertEquals(10,search.generations);assertEquals(4,search.stagnantGenerations);
        assertEquals(60,search.score);assertEquals(3,search.improvements);
    }
    @Test void inactivityTimerStartsBeforeAnySolutionAndIgnoresTheOverallRuntime(){
        var search=run(new HybridStopping(IDLE,1,2),.001,false);
        assertEquals(3,search.elapsed(),1e-8);assertEquals(1,search.lastImprovementAt,1e-8);
        assertTrue(search.generations>1);assertEquals(0,search.score);
    }
    @Test void eachStrictImprovementRestartsTheInactivityTimer(){
        var search=run(new HybridStopping(IDLE,1,2),.001,true);
        assertEquals(6,search.elapsed(),1e-8);assertEquals(4,search.lastImprovementAt,1e-8);
        assertEquals(60,search.score);assertEquals(3,search.improvements);
    }
    @Test void fixedTimeIgnoresBothStagnationLimitsAndDoesNotStopAtAnOptimalScore(){
        var search=run(new HybridStopping(TIME,1,.001),5,false);
        assertEquals(5,search.elapsed(),1e-8);assertTrue(search.generations>1);assertEquals(0,search.score);
        assertTrue(search.proved);
    }
    @Test void improvementsNearTheFixedDeadlineDoNotExtendIt(){
        var search=run(new HybridStopping(TIME,1000,15),5,true);
        assertEquals(5,search.elapsed(),1e-8);assertEquals(60,search.score);
        assertEquals(4,search.lastImprovementAt,1e-8);
    }
    @Test void inactivityDeadlineAlsoAppliesBeforeAnyFeasibleSolutionIsFound(){
        var time=new AtomicLong();var m=model(true);
        var search=new SearchEngine(m,new SearchEngine.Settings(7,.001,2,1,0,1),()->false,time::get){int[] greedySeed(){return null;}};
        search.hybridStopping=new HybridStopping(IDLE,1000,2);search.reportDiagnostics=false;
        search.run("Hybrid",(engine,child,optimize,seconds)->{time.addAndGet(500_000_000L);return null;});
        assertNull(search.best);assertEquals("UNKNOWN",search.status);assertEquals(2,search.elapsed(),1e-8);
    }
    @Test void cancellationStillInterruptsEveryMode(){
        for(var mode:HybridStopping.Mode.values()){
            var search=new SearchEngine(model(true),new SearchEngine.Settings(7,10,2,1,0,1),()->true);
            search.hybridStopping=new HybridStopping(mode,1000,15);search.reportDiagnostics=false;
            search.run("Hybrid",(run,child,optimize,seconds)->null);
            assertEquals("Cancelled",search.status);assertEquals(0,search.generations);
        }
    }
    @Test void selectedRuleAndValuesPersistWithoutInvalidatingAnExistingTimetable(){
        var p=SimpleProject.tinyDemo();var m=new ProblemData(p.toData());
        var search=new SearchEngine(m,new SearchEngine.Settings(7,1,2,1,0,1),()->false);
        search.inspect(new int[]{0,1,2,3,4,5});
        p.captureResult(search,"Hybrid");String hash=p.inputHash();
        p.hybridStopping=new HybridStopping(IDLE,1234,27);p.runtimeSeconds=120;
        var loaded=SimpleProject.parse(p.json());assertEquals(p.hybridStopping,loaded.hybridStopping);
        assertEquals(120,loaded.runtimeSeconds);assertEquals(hash,loaded.inputHash());assertNotNull(loaded.restoreResult());
    }
}
