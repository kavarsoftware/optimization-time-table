package edu.timetable;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.util.*;
import java.util.function.*;
/** Reusable labeled controls: a tooltip and an inline validation label accompany every input. */
final class SimpleFields {
    static final class Field<T> extends VBox {
        final Label error=new Label();final Supplier<T> read;final Function<T,String> check;final Node input;
        Field(String title,String help,Node input,Supplier<T> read,Function<T,String> check){
            super(5);this.input=input;this.read=read;this.check=check;
            var label=new Label(title);label.setLabelFor(input);label.getStyleClass().add("field-label");
            Tooltip.install(input,new Tooltip(help));Tooltip.install(label,new Tooltip(help));input.setAccessibleText(title+". "+help);
            error.getStyleClass().add("error");error.setWrapText(true);getChildren().addAll(label,input,error);setMaxWidth(Double.MAX_VALUE);
        }
        boolean valid(){String message=check.apply(read.get());error.setText(message);error.setManaged(!message.isEmpty());error.setVisible(!message.isEmpty());return message.isEmpty();}
        T value(){return read.get();}
        void watch(Runnable change){
            Runnable update=()->{valid();change.run();};
            if(input instanceof TextField f)f.textProperty().addListener((o,a,b)->update.run());
            else if(input instanceof Spinner<?> s)s.valueProperty().addListener((o,a,b)->update.run());
            else if(input instanceof ComboBox<?> c)c.valueProperty().addListener((o,a,b)->update.run());
            valid();
        }
    }
    static Field<String> text(String title,String value,boolean required){
        var f=new TextField(value);f.setPrefColumnCount(22);f.setMaxWidth(580);
        return new Field<>(title,"Text; example: "+(title.contains("Instructor")?"Dr. Narin":"Engineering")+"; "+(required?"1":"0")+"–100 characters.",f,()->f.getText().trim(),v->(!required&&v.isEmpty())||SimpleProject.validName(v)?"":"Enter "+(required?"1":"0")+"–100 characters, on one line.");
    }
    static Field<Integer> number(String title,int value,int min,int max){
        int step=title.startsWith("Time limit")||title.startsWith("Number of students")||title.startsWith("Capacity")?5:1;
        var f=new Spinner<Integer>(min,max,Math.clamp(value,min,max),step);f.setEditable(true);f.setPrefWidth(180);f.setMinWidth(Region.USE_PREF_SIZE);f.getStyleClass().add(Spinner.STYLE_CLASS_SPLIT_ARROWS_HORIZONTAL);
        String range="Allowed range: "+min+"–"+max;String[] inputError={""};boolean[] reverting={false};
        var field=new Field<Integer>(title,"Whole number; example: "+value+"; allowed "+min+"–"+max+". Type a number, then Enter or leave the field. Arrow step: "+step+".",f,f::getValue,v->!inputError[0].isEmpty()?inputError[0]:v<min||v>max?range:"");
        f.getEditor().textProperty().addListener((o,old,text)->{if(!reverting[0]){inputError[0]="";field.valid();}});
        f.getValueFactory().setConverter(new javafx.util.StringConverter<Integer>(){
            public String toString(Integer number){return number==null?"":number.toString();}
            public Integer fromString(String text){
                try{int parsed=Integer.parseInt(text.trim());if(parsed<min||parsed>max)throw new NumberFormatException();field.valid();return parsed;}
                catch(RuntimeException ex){inputError[0]=range;reverting[0]=true;try{f.getEditor().setText(Integer.toString(f.getValue()));}finally{reverting[0]=false;}field.valid();return f.getValue();}
            }
        });
        Runnable commit=()->{f.commitValue();reverting[0]=true;try{f.getEditor().setText(Integer.toString(f.getValue()));}finally{reverting[0]=false;}};
        f.getEditor().setOnAction(e->commit.run());
        f.getEditor().focusedProperty().addListener((o,old,focused)->{if(!focused)commit.run();});
        return field;
    }
    static <T> Field<T> choice(String title,T selected,List<T> choices){
        var f=new ComboBox<T>();f.getItems().setAll(choices);f.setValue(selected);f.setMaxWidth(580);
        return new Field<>(title,"Select one value: "+choices+".",f,f::getValue,v->v==null?"Choose a value.":"");
    }
    static final class Time extends VBox {
        final Spinner<Integer> hours,minutes;final Label error=new Label();
        Time(String title,int value){super(5);hours=new Spinner<>(0,23,value/60);minutes=new Spinner<>(0,59,value%60);hours.setEditable(false);minutes.setEditable(false);hours.setPrefWidth(90);minutes.setPrefWidth(90);
            var label=new Label(title);label.getStyleClass().add("field-label");label.setLabelFor(hours);
            var row=new HBox(6,hours,new Label(":"),minutes);getChildren().addAll(label,row,error);error.getStyleClass().add("error");error.setManaged(false);
            Tooltip.install(hours,new Tooltip("Hour (whole number), 0–23. Example: 9 = 09:00."));Tooltip.install(minutes,new Tooltip("Minute (whole number), 0–59. Example: 30 = half past."));
            hours.setAccessibleText(title+" hour, 0 to 23");minutes.setAccessibleText(title+" minute, 0 to 59");
        }
        int value(){return hours.getValue()*60+minutes.getValue();}
        void watch(Runnable r){hours.valueProperty().addListener((o,a,b)->r.run());minutes.valueProperty().addListener((o,a,b)->r.run());}
    }
    static Button button(String title,Runnable run){var b=new Button(title);b.setOnAction(e->run.run());return b;}
    static Label note(String text){var l=new Label(text);l.setWrapText(true);l.getStyleClass().add("muted");return l;}
}
