package edu.timetable;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class DynamicRestScoringTest {
    SimpleProject project(int subjects){
        var p=SimpleProject.tinyDemo();p.subjects=new ArrayList<>(p.subjects.subList(0,subjects));
        p.days.set(0,new SimpleProject.Day("Sunday",true,540,1020,30,List.of()));return p;
    }
    int at(ProblemData m,int event,String day,int start){
        for(int k=0;k<m.choices.get(event).size();k++){var p=m.choices.get(event).get(k);if(p.day().equals(day)&&p.start()==start)return k;}
        throw new AssertionError("Missing placement "+day+" "+start);
    }
    void pin(SimpleProject p,int event,String day,int start){p.pin(new SimpleProject.Placement(p.subjects.get(event).id(),1,day,start,"Tiny room"));}
    SearchEngine solve(SimpleProject p,String method){
        var run=new SearchEngine(new ProblemData(p.toData()),new SearchEngine.Settings(7,3,4,1,1,1000),()->false);
        run.reportDiagnostics=false;run.workers=1;run.warmStart=false;
        run.hybridStopping=new HybridStopping(HybridStopping.Mode.STAGNANT_GENERATIONS,2,15);
        if(method.equals("CP"))run.solveConstraints(new ConstraintSearch());else run.run(method,new ConstraintSearch());
        assertNotNull(run.best,method+" "+run.status);assertTrue(Evaluator.evaluate(run.m,run.best).valid());return run;
    }
    @Test void bothGoalsUseActualRestDurationsWithNoThirtyMinuteFallback(){
        for(int rest:new int[]{0,15,30,45,60})for(var goal:ScheduleObjective.Goal.values()){
            var p=project(2);p.optimizationGoal=goal;
            p.days.set(0,new SimpleProject.Day("Sunday",true,540,1020,30,rest==0?List.of():List.of(new SimpleProject.Break(630,630+rest))));
            pin(p,0,"Sunday",600);pin(p,1,"Sunday",630+rest+60);
            var m=new ProblemData(p.toData());var evaluation=Evaluator.evaluate(m,new int[2]);
            int expected=goal==ScheduleObjective.Goal.FEWER_GAPS?60:120;
            assertTrue(evaluation.valid());assertEquals(expected,evaluation.minutes(),goal+" rest="+rest);
            assertEquals(60,evaluation.gaps().getFirst().minutes());
            assertEquals(rest,evaluation.gaps().getFirst().rest());
            assertEquals(expected,solve(p,"CP").score);
        }
    }
    @Test void aGapExactlyCoveredByRestCostsZeroAndRestsNeverCreateNegativePenalties(){
        for(int rest:new int[]{0,15,45,60}){
            var p=project(2);
            p.days.set(0,new SimpleProject.Day("Sunday",true,540,1020,30,rest==0?List.of():List.of(new SimpleProject.Break(630,630+rest))));
            var m=new ProblemData(p.toData());int[] genes={at(m,0,"Sunday",600),at(m,1,"Sunday",630+rest)};
            var result=Evaluator.evaluate(m,genes);assertTrue(result.valid());assertEquals(0,result.minutes());
            assertEquals(0,result.gaps().getFirst().minutes());
        }
    }
    @Test void everyScheduledRestInsideTheSameGapIsDeducted(){
        for(var goal:ScheduleObjective.Goal.values()){
            var p=project(2);p.optimizationGoal=goal;
            p.days.set(0,new SimpleProject.Day("Sunday",true,540,1020,30,List.of(new SimpleProject.Break(570,585),new SimpleProject.Break(630,675))));
            pin(p,0,"Sunday",540);pin(p,1,"Sunday",735);
            var m=new ProblemData(p.toData());var result=Evaluator.evaluate(m,new int[2]);
            assertEquals(165,result.gaps().getFirst().elapsed());assertEquals(60,result.gaps().getFirst().rest());
            assertEquals(105,result.minutes());assertEquals(105,solve(p,"CP").score);
        }
    }
    @Test void restsOutsideTheGapAndRestsOnOtherDaysDoNotGiveFreeWaitingTime(){
        for(var goal:ScheduleObjective.Goal.values()){
            var p=project(2);p.optimizationGoal=goal;
            p.days.set(0,new SimpleProject.Day("Sunday",true,540,1020,30,List.of(new SimpleProject.Break(720,780))));
            p.days.set(1,new SimpleProject.Day("Monday",true,540,1020,30,List.of(new SimpleProject.Break(570,630))));
            pin(p,0,"Sunday",540);pin(p,1,"Sunday",600);
            var result=Evaluator.evaluate(new ProblemData(p.toData()),new int[2]);
            assertEquals(30,result.minutes());assertEquals(0,result.gaps().getFirst().rest());
            assertEquals(30,solve(p,"CP").score);
        }
    }
    @Test void differentDaysUseTheirOwnConfiguredRestWindows(){
        for(var goal:ScheduleObjective.Goal.values()){
            var p=project(4);p.optimizationGoal=goal;
            p.days.set(0,new SimpleProject.Day("Sunday",true,540,1020,30,List.of(new SimpleProject.Break(630,645))));
            p.days.set(1,new SimpleProject.Day("Monday",true,540,1020,30,List.of(new SimpleProject.Break(630,690))));
            pin(p,0,"Sunday",600);pin(p,1,"Sunday",705);pin(p,2,"Monday",600);pin(p,3,"Monday",750);
            var m=new ProblemData(p.toData());var parts=ScheduleObjective.parts(m,new int[4]);
            assertEquals(120,parts.gaps());assertEquals(goal==ScheduleObjective.Goal.FEWER_GAPS?0:120,parts.starts());
            assertEquals(parts.total(),solve(p,"CP").score);
        }
    }
    @Test void firstStartDelayStillUsesNineAmEvenWhenTheDayBeginsWithRest(){
        var p=project(2);p.optimizationGoal=ScheduleObjective.Goal.EARLIER_FINISH;
        p.days.set(0,new SimpleProject.Day("Sunday",true,540,1020,30,List.of(new SimpleProject.Break(540,600))));
        pin(p,0,"Sunday",600);pin(p,1,"Sunday",630);
        var parts=ScheduleObjective.parts(new ProblemData(p.toData()),new int[2]);
        assertEquals(0,parts.gaps());assertEquals(60,parts.starts());assertEquals(60,solve(p,"CP").score);
    }
    @Test void unavailableRemaindersStillCountAndCpHybridAndBruteForceAgree(){
        for(var goal:ScheduleObjective.Goal.values()){
            var p=project(2);p.optimizationGoal=goal;
            p.subjects.replaceAll(s->new SimpleProject.Subject(s.id(),s.stage(),s.group(),s.name(),s.type(),s.students(),1,60,s.instructor()));
            p.days.set(0,new SimpleProject.Day("Sunday",true,540,735,60,List.of(new SimpleProject.Break(620,665))));
            // Classes at 09:00 and 11:05: 65 elapsed minutes - 45 rest = 20 idle minutes.
            for(String method:List.of("CP","Hybrid","Brute-force")){
                var run=solve(p,method);assertEquals(20,run.score,goal+" "+method);assertTrue(run.proved);
            }
        }
    }
    @Test void editingRestChangesScoresAndInvalidatesTheOldSavedResultWithoutLosingInputs(){
        var p=project(2);p.days.set(0,new SimpleProject.Day("Sunday",true,540,1020,30,List.of(new SimpleProject.Break(630,660))));
        pin(p,0,"Sunday",600);pin(p,1,"Sunday",720);
        var original=solve(p,"CP");assertEquals(60,original.score);p.captureResult(original,"Hybrid");assertNotNull(p.restoreResult());
        p.days.set(0,new SimpleProject.Day("Sunday",true,540,1020,30,List.of(new SimpleProject.Break(630,690))));
        var loaded=SimpleProject.parse(p.json());assertEquals(2,loaded.subjects.size());assertEquals(1,loaded.rooms.size());
        assertEquals(60,loaded.days.getFirst().breaks().getFirst().end()-loaded.days.getFirst().breaks().getFirst().start());
        assertNull(loaded.restoreResult());assertEquals(30,solve(loaded,"CP").score);
    }
}
