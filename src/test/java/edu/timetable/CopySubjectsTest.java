package edu.timetable;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class CopySubjectsTest {
 @Test void copiesFieldsWithUniqueIdsSkipsNamesAndPreservesOriginal(){
  var p=SimpleProject.demo();var source=p.groups.getFirst();var target=new SimpleProject.Group(2,"B");
  var original=List.copyOf(p.subjects);var originals=original.stream().filter(s->s.stage()==source.stage()&&s.group().equals(source.name())).toList();
  assertEquals(5,p.copySubjects(source,List.of(source,target,target)));assertEquals(45,p.subjects.size());
  for(var a:originals){var b=p.subjects.stream().filter(s->s.stage()==target.stage()&&s.group().equals(target.name())&&s.name().equals(a.name())).findFirst().orElseThrow();assertNotEquals(a.id(),b.id());assertEquals(a.type(),b.type());assertEquals(a.students(),b.students());assertEquals(a.sessions(),b.sessions());assertEquals(a.minutes(),b.minutes());assertEquals(a.instructor(),b.instructor());}
  assertEquals(original,p.subjects.subList(0,40));assertEquals(45,p.subjects.stream().map(SimpleProject.Subject::id).distinct().count());assertEquals(0,p.copySubjects(source,List.of(target)));
  assertEquals(45,SimpleProject.parse(p.json()).subjects.size());assertTrue(p.health().stream().noneMatch(SimpleProject.Issue::error));
 }
}
