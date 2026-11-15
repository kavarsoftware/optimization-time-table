package edu.timetable;
import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
/** A recurring week is expanded only on eligible term dates. */
final class TermCalendar {
    static List<LocalDate> dates(Data data){
        if(data.rows.get("Term").isEmpty())return List.of();
        String[] term=data.rows.get("Term").getFirst();
        LocalDate start=LocalDate.parse(term[0]),end=LocalDate.parse(term[1]);
        ProblemData.require(!end.isBefore(start),"Term end must follow start.");
        Set<DayOfWeek> days=new HashSet<>();
        for(var day:data.rows.get("Days"))days.add(DayOfWeek.valueOf(day[0].toUpperCase(Locale.ROOT)));
        Set<LocalDate> holidays=new HashSet<>();if(!term[2].isBlank())for(String date:term[2].split(","))holidays.add(LocalDate.parse(date.trim()));
        var exams=new ArrayList<LocalDate[]>();if(!term[3].isBlank())for(String range:term[3].split(",")){
            var r=range.trim().split("/");ProblemData.require(r.length==2,"Exam range must be YYYY-MM-DD/YYYY-MM-DD");
            var a=LocalDate.parse(r[0]);var b=LocalDate.parse(r[1]);ProblemData.require(!b.isBefore(a),"Invalid examination range");exams.add(new LocalDate[]{a,b});
        }
        return start.datesUntil(end.plusDays(1)).filter(d->days.contains(d.getDayOfWeek())&&!holidays.contains(d)&&exams.stream().noneMatch(r->!d.isBefore(r[0])&&!d.isAfter(r[1]))).toList();
    }
    static long weeks(Data d){return dates(d).stream().map(day->day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))).distinct().count();}
}
