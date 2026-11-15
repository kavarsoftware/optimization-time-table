package edu.timetable;
import java.util.*;
import static edu.timetable.Data.*;
/** Legacy data-time validation retained for existing verification tools; no UI. */
final class AvailabilityEditor {
    static void bounds(Data d,String day){
        var windows=d.rows.get("Periods").stream().filter(r->r[0].equals(day)).toList();
        if(windows.isEmpty())return;
        d.rows.get("Days").stream().filter(r->r[0].equals(day)).forEach(r->{
            r[1]=clock(windows.stream().mapToInt(p->minute(p[2])).min().orElse(480));
            r[2]=clock(windows.stream().mapToInt(p->minute(p[3])).max().orElse(900));
        });
    }
    static void validate(Data d){
        for(var day:d.rows.get("Days")){
            var windows=d.rows.get("Periods").stream().filter(r->r[0].equals(day[0])).toList();
            ProblemData.require(windows.stream().anyMatch(r->r[4].equals("Teaching")),"Add available time for "+day[0]+", or deselect that teaching day.");
            for(int i=0;i<windows.size();i++){
                var a=windows.get(i);ProblemData.require(minute(a[3])>minute(a[2]),day[0]+": finish must be later than start.");
                for(int j=0;j<i;j++){var b=windows.get(j);ProblemData.require(!ProblemData.overlap(minute(a[2]),minute(a[3]),minute(b[2]),minute(b[3])),day[0]+": two windows or breaks overlap. Adjust or remove one.");}
            }
            bounds(d,day[0]);
        }
    }
}
