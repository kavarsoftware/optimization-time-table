package edu.timetable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class SavedResultTest {
 @TempDir Path temp;
 @Test void restoresVerifiedPlacementsAndRejectsStaleOrInvalidResults()throws Exception{
  var p=SimpleProject.tinyDemo();assertNull(SimpleProject.parse(p.json()).result);
  var search=new SearchEngine(new ProblemData(p.toData()),new SearchEngine.Settings(7,5,16,100,.12,1000),()->false);search.run("Brute-force",new ConstraintSearch());assertNotNull(search.best);
  p.captureResult(search,"Brute-force");var file=temp.resolve("saved.json");p.save(file);var loaded=SimpleProject.load(file);assertEquals(1,loaded.schemaVersion);var restored=loaded.restoreResult();assertNotNull(restored);assertTrue(Evaluator.evaluate(restored.m,restored.best).valid());assertEquals(search.score,restored.score);assertArrayEquals(search.best,restored.best);
  loaded.university="Edited inputs";assertNull(loaded.restoreResult());loaded=SimpleProject.load(file);
  var saved=loaded.result;loaded.result=new SimpleProject.SavedResult(saved.method(),saved.seed(),saved.score()+1,saved.placements(),saved.inputHash());assertNull(loaded.restoreResult());
  loaded.result=new SimpleProject.SavedResult(saved.method(),saved.seed(),saved.score(),List.of(),saved.inputHash());assertNull(loaded.restoreResult());
  loaded.result=null;assertNull(SimpleProject.parse(loaded.json()).restoreResult());
 }
}
