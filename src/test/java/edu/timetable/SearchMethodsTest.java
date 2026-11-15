package edu.timetable;

import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;

class SearchMethodsTest {
    SimpleProject project(){
        var p=SimpleProject.tinyDemo();p.subjects=new ArrayList<>(p.subjects.subList(0,1));return p;
    }
    @Test void onlyHybridRandomAndBruteForceAreAvailable(){
        assertEquals(List.of("Hybrid","Random","Brute-force"),SearchEngine.METHODS);
        for(String removed:List.of("CP","GA","Exhaustive","CP + Genetic")){
            var search=project().newSearch(1,()->false);
            assertThrows(IllegalArgumentException.class,()->search.run(removed,null));
        }
    }
    @Test void eachNewRunGetsFreshRandomnessWithoutEditingTheInputs(){
        var p=project();String before=p.json();
        var previous=p.newSearch(1,()->false);
        for(int i=0;i<20;i++){
            var next=p.newSearch(1,()->false);
            assertNotEquals(previous.settings.seed(),next.settings.seed());
            assertEquals(previous.m.data.text(),next.m.data.text());assertEquals(p.hybridStopping,next.hybridStopping);
            previous=next;
        }
        assertEquals(before,p.json());
    }
    @Test void equallyGoodTimetablesCanReplaceEachOtherWithoutCountingAsImprovement(){
        var m=new ProblemData(project().toData());var time=new AtomicLong();
        var search=new SearchEngine(m,new SearchEngine.Settings(7,5,4,1,1,1),()->false,time::get);
        search.hybridActive=true;search.inspect(new int[]{0});
        time.set(1_000_000_000L);search.stagnantGenerations=4;
        search.inspect(new int[]{1});
        assertArrayEquals(new int[]{1},search.best);assertEquals(0,search.score);
        assertEquals(1,search.improvements);assertEquals(0,search.lastImprovementAt);
        assertEquals(4,search.stagnantGenerations);
    }
    @Test void differentSeedsExploreDifferentValidTimetablesForTheSameInputs(){
        var m=new ProblemData(project().toData());var results=new HashSet<String>();
        for(int seed=7;seed<=10;seed++){
            var search=new SearchEngine(m,new SearchEngine.Settings(seed,5,4,1,1,1),()->false);
            search.hybridStopping=new HybridStopping(HybridStopping.Mode.STAGNANT_GENERATIONS,2,15);
            search.workers=1;search.reportDiagnostics=false;search.run("Hybrid",new ConstraintSearch());
            assertTrue(Evaluator.evaluate(m,search.best).valid());assertEquals(0,search.score);
            assertTrue(search.mutations>0);results.add(Arrays.toString(search.best));
        }
        assertTrue(results.size()>1,"Different seeds should explore different placements");
    }
}
