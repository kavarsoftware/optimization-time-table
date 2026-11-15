package edu.timetable;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class RemainingFeaturesTest {
 static SearchEngine feasible(SimpleProject p){var s=new SearchEngine(new ProblemData(p.toData()),new SearchEngine.Settings(7,5,16,100,.12,1),()->false);s.run("Random",new ConstraintSearch());assertNotNull(s.best);assertTrue(Evaluator.evaluate(s.m,s.best).valid());return s;}
 @Test void pinsUseExistingLockIndicesAndNameConflicts(){
  var p=SimpleProject.tinyDemo();var first=p.subjects.getFirst();p.subjects.set(0,new SimpleProject.Subject(first.id(),1,"A",first.name(),first.type(),30,2,30,first.instructor()));
  var pin=new SimpleProject.Placement("tiny-4",1,"Sunday",540,"Tiny room");p.pin(pin);assertEquals(5,p.eventIndex(pin));assertEquals("5",p.toData().rows.get("Locks").getFirst()[0]);assertEquals("pin",p.toData().rows.get("Locks").getFirst()[1]);var m=new ProblemData(p.toData());assertEquals(1,m.choices.get(5).size());assertEquals(540,m.choices.get(5).getFirst().start());
  assertEquals(pin,SimpleProject.parse(p.json()).pins().getFirst());p.pin(new SimpleProject.Placement("tiny-0",1,"Sunday",540,"Tiny room"));assertTrue(p.health().stream().anyMatch(i->i.error()&&i.message().contains("Conflicting pins")&&i.message().contains("Tiny subject 5")));assertThrows(IllegalArgumentException.class,p::toData);p.unpin(pin);assertFalse(p.isPinned(pin));
 }
 @Test void daysOffRestrictEveryLessonAndWarnWhenAllSelectedDaysAreOff(){
  var p=SimpleProject.tinyDemo();p.days.set(1,new SimpleProject.Day("Monday",true,540,720,30,List.of()));p.daysOff=new LinkedHashMap<>();p.daysOff.put("Tiny instructor",List.of("Sunday"));var d=p.toData();assertEquals("Monday@00:00-23:59",d.rows.get("Teachers").getFirst()[2]);var m=new ProblemData(d);assertTrue(m.choices.stream().flatMap(List::stream).allMatch(t->t.day().equals("Monday")));assertEquals(List.of("Sunday"),SimpleProject.parse(p.json()).off("Tiny instructor"));
  p.daysOff.put("Tiny instructor",List.of("Sunday","Monday"));assertTrue(p.health().stream().anyMatch(i->!i.error()&&i.message().contains("every teaching day off")));assertTrue(new ProblemData(p.toData()).choices.stream().allMatch(List::isEmpty));
 }
 @Test void printHtmlEscapesTextAndCreatesLandscapePagesPerSection()throws Exception{
  var p=SimpleProject.tinyDemo();var first=p.subjects.getFirst();p.subjects.set(0,new SimpleProject.Subject(first.id(),1,"A","Subject <script>","Theory",30,1,30,"Other instructor"));var s=feasible(p);String stages=SimpleExport.printHtml(s,true,"All"),teachers=SimpleExport.printHtml(s,false,"All");assertTrue(stages.contains("A4 landscape"));assertTrue(stages.contains("@media print"));assertTrue(stages.contains("&lt;script&gt;"));assertFalse(stages.contains("Subject <script>"));assertEquals(1,stages.split("class='print-page'",-1).length-1);assertEquals(2,teachers.split("class='print-page'",-1).length-1);String only=SimpleExport.printHtml(s,false,"Other instructor");assertEquals(1,only.split("class='print-page'",-1).length-1);assertTrue(only.contains("Save as PDF"));
 }
 @Test void jointClassesProduceOneRowTwoGroupsAndCombinedEnrollment(){
  var p=SimpleProject.demo();var a=p.subjects.get(0);var b=p.subjects.get(5);p.join(a.id(),b.id());assertTrue(p.health().stream().anyMatch(i->i.error()&&i.message().contains("70 students")));p.rooms.add(new SimpleProject.Room("Joint hall","Classroom",80));var d=p.toData();assertEquals(39,d.rows.get("Subjects").size());var m=new ProblemData(d);assertEquals(62,m.events.size());assertEquals(70,m.events.getFirst().seats());assertEquals(Set.of("1 A","1 B"),new HashSet<>(m.events.getFirst().groups()));assertTrue(m.choices.getFirst().stream().allMatch(t->t.room().equals("Joint hall")));assertFalse(d.rows.get("Subjects").stream().anyMatch(row->row[0].equals(b.id())));assertEquals(b.id(),SimpleProject.parse(p.json()).jointWith.get(a.id()));assertThrows(IllegalArgumentException.class,()->p.join(a.id(),p.subjects.get(10).id()));
 }
 @Test void movesOfferOnlyValidAlternativesPreserveUndoAndHonorPins(){
  var p=SimpleProject.tinyDemo();p.rooms.add(new SimpleProject.Room("Spare","Classroom",40));var original=feasible(p);int[] before=original.best.clone();var choices=p.moveOptions(original,0);assertFalse(choices.isEmpty());for(int option:choices){int[] candidate=before.clone();candidate[0]=option;assertTrue(Evaluator.evaluate(original.m,candidate).valid());}assertThrows(IllegalArgumentException.class,()->p.move(original,0,before[0]));var moved=p.move(original,0,choices.getFirst());assertArrayEquals(before,original.best);assertEquals(Evaluator.evaluate(moved.m,moved.best).minutes(),moved.score);p.captureResult(moved,"Manual");assertNotNull(p.restoreResult());var undo=original;assertArrayEquals(before,undo.best);p.pin(p.placement(moved,0));assertTrue(p.moveOptions(moved,0).isEmpty());
 }
}
