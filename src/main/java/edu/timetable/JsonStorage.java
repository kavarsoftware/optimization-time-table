package edu.timetable;
import com.google.gson.*;
import java.util.*;
/** Versioned UTF-8 storage. Legacy parsers remain import-only. */
final class JsonStorage {
    static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();
    record Dataset(int schemaVersion,Map<String,List<String[]>> tables){}
    record Project(int schemaVersion,List<Dataset> datasets){}
    static String write(Data d){return JSON.toJson(new Dataset(2,d.rows));}
    static Data read(String json){
        Dataset file=JSON.fromJson(json,Dataset.class);
        if(file==null||file.schemaVersion()!=2||file.tables()==null)throw new IllegalArgumentException("Unsupported dataset schema; expected version 2.");
        return data(file);
    }
    static Data data(Dataset source){
        var d=new Data();
        source.tables().forEach((k,rows)->{
            if(!Data.fields.containsKey(k))throw new IllegalArgumentException("Unknown table "+k);
            for(var row:rows){
                if(row==null||row.length!=Data.fields.get(k).split("\\|").length||Arrays.stream(row).anyMatch(Objects::isNull))
                    throw new IllegalArgumentException("Malformed "+k+" record");
                d.rows.get(k).add(row.clone());
            }
        });return d;
    }
    static String writeProject(Portfolio p){return JSON.toJson(new Project(2,p.entries.values().stream().map(d->new Dataset(2,d.rows)).toList()));}
    static Portfolio readProject(String json){
        var source=JSON.fromJson(json,Project.class);
        if(source==null||source.schemaVersion()!=2||source.datasets()==null||source.datasets().isEmpty())throw new IllegalArgumentException("Invalid project JSON");
        var p=new Portfolio();for(var d:source.datasets()){if(d.schemaVersion()!=2)throw new IllegalArgumentException("Unsupported dataset version");p.put(data(d));}return p;
    }
}
