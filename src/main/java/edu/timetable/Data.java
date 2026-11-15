package edu.timetable;

import java.nio.file.*;
import java.io.*;
import java.util.*;
import java.time.LocalTime;

public class Data {
    static final Map<String,String> fields = new LinkedHashMap<>();
    static {
        fields.put("Campus","University|Department|Semester|Slot min|Default duration|Default break|Mode|Reserve room|Score");
        fields.put("Institution","College|Semester count|Selected number|Total students");
        fields.put("Term","Start date|End date|Holidays|Exam ranges|Span adjacent periods");
        fields.put("GroupCounts","Group|Students");
        fields.put("Workloads","Teacher|Daily minutes|Weekly minutes|Preferred days");
        fields.put("Parts","Code|Groups|Part room type|Weekly minutes|Allowed lengths|Sessions");
        fields.put("Weights","Student gaps|Instructor gaps|Balance|Early late|Lunch|Single class days|Room consistency|Wasted seats|Preferred days");
        fields.put("Preferences","Earliest preferred|Latest preferred|Lunch start|Lunch finish|Workers");
        fields.put("SearchObjective","Goal");
        fields.put("Locks","Event index|Kind|Day|Start|Room");
        fields.put("Periods","Day|Name|Start|Finish|Kind");
        fields.put("Days","Day|Open|Close");
        fields.put("Stages","Stage");
        fields.put("Groups","Group|Stage");
        fields.put("Teachers","Teacher|Title|Availability");
        fields.put("Rooms","Room|Type|Seats|Availability");
        fields.put("Subjects","Code|Name|Teacher|Groups|Student IDs|Seats|Room type|Weekly meetings|Duration|Break|Mode|Reserve room");
        fields.put("Rules","Enabled|Rule|First code|Second code|Group");
    }
    final Map<String,List<String[]>> rows=new LinkedHashMap<>();
    Data(){fields.keySet().forEach(k->rows.put(k,new ArrayList<>()));}
    static Data load(Path file) throws IOException {
        return parse(Files.readString(file));
    }
    static Data parse(String content){
        if(content.stripLeading().startsWith("{"))return JsonStorage.read(content);
        Data data=new Data(); String table="";
        for(String line:content.split("\\R")){
            if(line.isBlank()) continue;
            if(line.startsWith("[") && line.endsWith("]")){table=line.substring(1,line.length()-1);continue;}
            String[] row=line.split("\\|",-1);
            if(!fields.containsKey(table)||row.length!=fields.get(table).split("\\|").length)
                throw new IllegalArgumentException("Invalid row in "+table+": "+line);
            data.rows.get(table).add(row);
        }
        return data;
    }
    String text(){
        return JsonStorage.write(this);
    }
    void save(Path path)throws IOException{
        Path temp=Files.createTempFile(path.toAbsolutePath().getParent(),"schedule-", ".tmp");
        try {Files.writeString(temp,text());Files.move(temp,path,StandardCopyOption.REPLACE_EXISTING);}
        finally {Files.deleteIfExists(temp);}
    }
    static int minute(String s){LocalTime t=LocalTime.parse(s);return t.getHour()*60+t.getMinute();}
    static String clock(int m){return "%02d:%02d".formatted(m/60,m%60);}
    static Set<String> names(String value){return value.isBlank()?Set.of():Set.of(value.split("\\s*,\\s*"));}
    static int positive(String v){int n=Integer.parseInt(v);if(n<=0)throw new IllegalArgumentException("Expected positive number: "+v);return n;}
    static boolean flag(String v){if(!Set.of("yes","no").contains(v))throw new IllegalArgumentException("Expected yes or no: "+v);return v.equals("yes");}
    static String or(String a,String fallback){return a.isBlank()?fallback:a;}
}
