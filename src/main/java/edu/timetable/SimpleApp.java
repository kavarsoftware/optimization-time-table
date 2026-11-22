package edu.timetable;

import javafx.application.*;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.*;
import javafx.geometry.Insets;
import javafx.animation.*;
import javafx.concurrent.Task;
import javafx.util.Duration;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import java.nio.file.*;
import java.util.*;
import java.util.function.*;
import java.io.*;
import static edu.timetable.SimpleFields.*;

/** Seven small screens. All optimization remains in the existing solver classes. */
public final class SimpleApp extends Application {
    SimpleProject project=SimpleProject.demo();
    final String[] steps={"University","Stages & groups","Subjects","Days & times","Rooms","Review","Generate"};
    final BorderPane root=new BorderPane();final VBox page=new VBox(8),sidebar=new VBox(8),content=new VBox(14);
    final Label status=new Label("Editable sample department • not saved"),pageError=new Label(),runStatus=new Label();
    final Button next=button("Next →",()->go(this.step+1)),back=button("← Back",()->go(this.step-1));
    final ProgressBar progress=new ProgressBar(0);final ProgressIndicator wizardProgress=new ProgressIndicator(1.0/7);
    final SimpleFields.Field<Integer> limit=number("Time limit (seconds)",60,1,86400);
    final SimpleFields.Field<String> method=choice("Method","Hybrid",SearchEngine.METHODS);
    SimpleFields.Field<ScheduleObjective.Goal> solutionGoal;
    SimpleFields.Field<HybridStopping.Mode> stopMode;
    SimpleFields.Field<Integer> hybridPopulation;
    SimpleFields.Field<Integer> stopGenerations,stopSeconds;
    final CheckBox feasibleGenerator=new CheckBox("Feasible generator");
    final Path recovery=Path.of(System.getProperty("user.home"),".uniplan-simple","autosave.json");
    final PauseTransition autosave=new PauseTransition(Duration.millis(500));
    Stage stage;int step;boolean busy,dirty;Path currentFile;SearchEngine result;volatile SearchEngine live;Task<SearchEngine> task;
    String generatedMethod="Hybrid";
    SearchEngine undoMove;String undoMethod;
    int selectedStage=1;String selectedGroup="A";
    final List<Node> fileButtons=new ArrayList<>();
    final List<SimpleFields.Field<?>> visibleFields=new ArrayList<>();
    final ComboBox<String> stageFilter=new ComboBox<>(),groupFilter=new ComboBox<>(),roomFilter=new ComboBox<>(),teacherFilter=new ComboBox<>();
    final VBox timetable=new VBox(12);final List<String[]> visibleRows=new ArrayList<>();
    final Label gridDetail=new Label("Select a class to see its details.");
    @Override public void start(Stage primary){
        method.watch(()->{if(step==6&&!busy)render();});
        limit.watch(()->{if(project.runtimeSeconds!=limit.value()){project.runtimeSeconds=limit.value();searchPreferencesChanged();}});
        feasibleGenerator.setSelected(true);feasibleGenerator.setTooltip(new Tooltip("Random: use constructive feasible placement instead of uniform random genes."));stage=primary;stage.setTitle("UniPlan Simple");
        var brand=new Label("UniPlan Simple");brand.getStyleClass().add("brand");
        var subtitle=new Label("A clear weekly timetable for your department");subtitle.getStyleClass().add("header-note");
        var newFile=button("New",()->{if(discardChanges())replace(SimpleProject.demo(),null);});newFile.setId("new-project");var open=button("Open JSON",this::open);var save=button("Save JSON",this::save);
        var demo=button("Restore default data",()->{if(discardChanges())replace(SimpleProject.demo(),null);});var recover=button("Recover autosave",()->{if(!discardChanges())return;try{replace(SimpleProject.load(recovery),null);}catch(Exception ex){showError(ex);}});
        var tiny=button("Load tiny demo",()->{if(discardChanges())replace(SimpleProject.tinyDemo(),null);});fileButtons.addAll(List.of(newFile,open,save,demo,recover,tiny));
        var actions=new FlowPane(8,8,newFile,open,save,demo,recover,tiny);var heading=new VBox(8,brand,subtitle);
        var header=new HBox(8,heading,actions);header.getStyleClass().add("header");HBox.setHgrow(heading,Priority.ALWAYS);header.setAlignment(javafx.geometry.Pos.CENTER_LEFT);root.setTop(header);
        sidebar.getStyleClass().add("sidebar");sidebar.setPrefWidth(205);wizardProgress.setPrefSize(36,36);
        for(int i=0;i<steps.length;i++){final int index=i;var b=button((i+1)+"   "+steps[i],()->go(index));b.setMaxWidth(Double.MAX_VALUE);b.setId("step-"+i);sidebar.getChildren().add(b);}
        sidebar.getChildren().addAll(new Separator(),wizardProgress,note("Four stages. One weekly schedule."));root.setLeft(sidebar);
        var scroller=new ScrollPane(page);scroller.setFitToWidth(true);scroller.setPannable(true);VBox.setVgrow(scroller,Priority.ALWAYS);page.setPadding(new Insets(26));
        pageError.getStyleClass().add("error");pageError.setWrapText(true);
        var spacer=new Region();HBox.setHgrow(spacer,Priority.ALWAYS);var nav=new HBox(8,back,spacer,next);nav.setPadding(new Insets(8,24,18,24));
        content.getChildren().addAll(scroller,pageError,nav);root.setCenter(content);
        status.setPadding(new Insets(10,24,12,24));root.setBottom(status);
        autosave.setOnFinished(e->{try{project.save(recovery);status.setText("Unsaved edits • recovery copy saved");}catch(Exception ex){status.setText("Autosave failed: "+ex.getMessage());}});
        Scene scene=new Scene(root,1420,960);scene.getStylesheets().add(getClass().getResource("/studio.css").toExternalForm());stage.setScene(scene);stage.setMinWidth(1050);stage.setMinHeight(740);
        stage.setOnCloseRequest(e->{autosave.stop();try{if(dirty)project.save(recovery);}catch(Exception ex){System.err.println("Autosave on exit: "+ex.getMessage());}if(task!=null)task.cancel();});
        render();stage.show();if(Boolean.getBoolean("simple.smoke"))Platform.runLater(()->SimpleSmoke.run(this));
    }
    void changed(){dirty=true;result=null;project.result=null;undoMove=null;autosave.playFromStart();status.setText("Unsaved edits • previous timetable cleared");}
    void searchPreferencesChanged(){dirty=true;autosave.playFromStart();}
    void replace(SimpleProject p,Path file){if(busy)return;autosave.stop();var saved=p.result;project=p;currentFile=file;step=0;selectedStage=1;selectedGroup=project.groups.stream().filter(g->g.stage()==1).map(SimpleProject.Group::name).findFirst().orElse("A");changed();project.result=saved;render();if(saved!=null){var restored=project.restoreResult();if(restored!=null){result=restored;configureFilters();runStatus.setText("Restored "+saved.method()+" • score "+restored.scoreText()+" • zero hard violations");step=6;render();status.setText("Saved timetable restored and verified.");}else{project.result=null;status.setText("Inputs changed - regenerate");}}}
    boolean discardChanges(){return !dirty||new Alert(Alert.AlertType.CONFIRMATION,"Replace the current unsaved inputs? Save JSON first if you want to keep them.",ButtonType.CANCEL,ButtonType.OK).showAndWait().orElse(ButtonType.CANCEL)==ButtonType.OK;}
    void open(){if(!discardChanges())return;var chooser=new FileChooser();chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("UniPlan Simple JSON","*.json"));var f=chooser.showOpenDialog(stage);if(f!=null)try{var p=SimpleProject.load(f.toPath());replace(p,f.toPath());if(p.result!=null||!status.getText().equals("Inputs changed - regenerate"))status.setText("Opened "+f.getName()+(result==null?"":" • timetable restored and verified"));}catch(Exception ex){showError(ex);}}
    void save(){var chooser=new FileChooser();chooser.setInitialFileName(currentFile==null?"MyDepartment.json":currentFile.getFileName().toString());chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("UniPlan Simple JSON","*.json"));var f=chooser.showSaveDialog(stage);if(f!=null)try{autosave.stop();project.save(f.toPath());currentFile=f.toPath();dirty=false;status.setText("Saved "+f.getName());}catch(Exception ex){showError(ex);}}
    void showError(Throwable ex){new Alert(Alert.AlertType.ERROR,Objects.toString(ex.getMessage(),ex.toString()),ButtonType.OK).showAndWait();}
    boolean validatePage(){
        boolean ok=true;for(var field:visibleFields)ok=field.valid()&&ok;
        var problems=project.health().stream().filter(i->i.error()&&i.step()==step).toList();
        pageError.setText(problems.isEmpty()?"":problems.getFirst().message());return ok&&problems.isEmpty();
    }
    void go(int target){if(busy||target<0||target>=steps.length)return;if(target>step&&!validatePage())return;step=target;render();var fade=new FadeTransition(Duration.millis(150),page);fade.setFromValue(.25);fade.setToValue(1);fade.play();}
    void render(){
        visibleFields.clear();page.getChildren().clear();page.setSpacing(8);pageError.setText("");
        for(int i=0;i<7;i++){sidebar.getChildren().get(i).getStyleClass().remove("step-active");if(i==step)sidebar.getChildren().get(i).getStyleClass().add("step-active");}
        wizardProgress.setProgress((step+1)/7.0);back.setDisable(step==0||busy);next.setVisible(step<6);next.setManaged(step<6);
        var title=new Label(steps[step]);title.getStyleClass().add("page-title");page.getChildren().add(title);
        switch(step){case 0->institution();case 1->groups();case 2->subjects();case 3->days();case 4->rooms();case 5->review();case 6->generation();default->throw new IllegalStateException();}
    }
    void institution(){
        var u=text("University (text)",project.university,true);var c=text("College (text)",project.college,true);var d=text("Department (text)",project.department,true);
        u.watch(()->{project.university=u.value();changed();});c.watch(()->{project.college=c.value();changed();});d.watch(()->{project.department=d.value();changed();});
        visibleFields.addAll(List.of(u,c,d));page.getChildren().addAll(u,c,d);
    }
    <T> TableView<T> table(List<T> rows,String[] headings,List<Function<T,String>> values){
        var t=new TableView<T>(FXCollections.observableArrayList(rows));t.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);t.setPrefHeight(Math.min(380,Math.max(160,rows.size()*42+42)));t.setPlaceholder(note("No records yet. Use Add to begin."));
        for(int i=0;i<headings.length;i++){final int k=i;var col=new TableColumn<T,String>(headings[i]);col.setCellValueFactory(v->new ReadOnlyStringWrapper(values.get(k).apply(v.getValue())));t.getColumns().add(col);}return t;
    }
    void groups(){
        page.getChildren().add(note("Stages 1–4 are fixed. Each stage needs at least one group. Subjects belong to one group."));
        var t=table(project.groups,new String[]{"Stage (int)","Group (text)"},List.of(g->""+g.stage(),SimpleProject.Group::name));
        page.getChildren().addAll(new HBox(8,button("Add group",()->groupDialog(null)),button("Edit selected",()->{var g=t.getSelectionModel().getSelectedItem();if(g!=null)groupDialog(g);}),button("Remove selected",()->{
            var g=t.getSelectionModel().getSelectedItem();if(g==null)return;
            if(project.subjects.stream().anyMatch(s->s.stage()==g.stage()&&s.group().equals(g.name()))){pageError.setText("Remove this group's subjects before removing the group.");return;}
            project.groups.remove(g);changed();render();})),t);
    }
    void groupDialog(SimpleProject.Group old){
        var stageField=number("Stage (whole number)",old==null?selectedStage:old.stage(),1,4);var name=text("Group (text)",old==null?"B":old.name(),true);
        form("Group",List.of(stageField,name),()->{
            var g=new SimpleProject.Group(stageField.value(),name.value());
            if(!SimpleProject.validGroup(g.name()))return "Group name cannot contain a comma.";
            if(project.groups.stream().anyMatch(x->x!=old&&x.stage()==g.stage()&&x.name().equalsIgnoreCase(g.name())))return "This group already exists in this stage.";return "";
        },()->{
            var g=new SimpleProject.Group(stageField.value(),name.value());
            if(old==null)project.groups.add(g);else{
                project.groups.set(project.groups.indexOf(old),g);
                project.subjects.replaceAll(s->s.stage()==old.stage()&&s.group().equals(old.name())?new SimpleProject.Subject(s.id(),g.stage(),g.name(),s.name(),s.type(),s.students(),s.sessions(),s.minutes(),s.instructor()):s);
            }selectedStage=g.stage();selectedGroup=g.name();project.groups.sort(Comparator.comparingInt(SimpleProject.Group::stage).thenComparing(SimpleProject.Group::name));changed();render();
        });
    }
    void subjects(){
        var stageChoice=choice("Selected stage (int)",selectedStage,List.of(1,2,3,4));
        var names=project.groups.stream().filter(g->g.stage()==selectedStage).map(SimpleProject.Group::name).toList();if(!names.contains(selectedGroup))selectedGroup=names.isEmpty()?null:names.getFirst();
        var groupChoice=choice("Selected group (text)",selectedGroup,names);
        stageChoice.watch(()->{selectedStage=stageChoice.value();render();});groupChoice.watch(()->{selectedGroup=groupChoice.value();render();});
        page.getChildren().addAll(new HBox(8,stageChoice,groupChoice),note("New session length defaults to the first selected day's class length (currently "+project.defaultMinutes()+" minutes). Existing subject lengths stay unchanged when daily times change."));
        var rows=project.subjects.stream().filter(s->s.stage()==selectedStage&&s.group().equals(selectedGroup)).toList();
        var t=table(rows,new String[]{"Subject","Type","Students","Sessions/week","Length (min)","Instructor"},List.of(SimpleProject.Subject::name,SimpleProject.Subject::type,s->""+s.students(),s->""+s.sessions(),s->""+s.minutes(),s->s.instructor().isBlank()?"Not assigned":s.instructor()));
        page.getChildren().addAll(new FlowPane(8,8,button("Instructors / days off…",this::instructors),button("Joint with group…",()->{var selected=t.getSelectionModel().getSelectedItem();if(selected!=null)jointDialog(selected);else pageError.setText("Select a subject first.");}),button("Copy subjects to…",this::copySubjects),button("Add subject",()->{if(selectedGroup==null){pageError.setText("Add a group to this stage first.");return;}subjectDialog(null);}),button("Edit selected",()->{var s=t.getSelectionModel().getSelectedItem();if(s!=null)subjectDialog(s);}),button("Remove selected",()->{var s=t.getSelectionModel().getSelectedItem();if(s!=null){project.removeSubject(s.id());changed();render();}})),t,note(rows.size()+" subjects for Stage "+selectedStage+", Group "+Objects.toString(selectedGroup,"—")+" • "+project.subjects.size()+" in the whole department"));
    }
    void copySubjects(){
        if(selectedGroup==null)return;var source=new SimpleProject.Group(selectedStage,selectedGroup);
        var dialog=new Dialog<ButtonType>();dialog.initOwner(stage);dialog.setTitle("Copy subjects to…");
        dialog.getDialogPane().getStylesheets().add(getClass().getResource("/studio.css").toExternalForm());
        var body=new VBox(8,note("Copies subjects with new IDs. Existing names are skipped. Instructors remain shared."));var selected=new LinkedHashMap<SimpleProject.Group,CheckBox>();
        for(var group:project.groups)if(!group.equals(source)){var check=new CheckBox(group.toString());selected.put(group,check);body.getChildren().add(check);}
        var scroll=new ScrollPane(body);scroll.setFitToWidth(true);scroll.setPrefViewportHeight(300);dialog.getDialogPane().setContent(scroll);dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL,ButtonType.OK);
        if(dialog.showAndWait().orElse(ButtonType.CANCEL)==ButtonType.OK){int count=project.copySubjects(source,selected.entrySet().stream().filter(e->e.getValue().isSelected()).map(Map.Entry::getKey).toList());if(count>0){changed();render();}status.setText("Copied "+count+" subjects; existing names skipped.");}
    }
    void subjectDialog(SimpleProject.Subject old){
        var name=text("Subject name (text)",old==null?"":old.name(),true);var type=choice("Type",old==null?"Theory":old.type(),List.of("Theory","Lab"));
        var count=number("Number of students (whole number)",old==null?30:old.students(),1,SimpleProject.MAX_ENROLLMENT);var sessions=number("Sessions per week (whole number)",old==null?1:old.sessions(),1,20);var minutes=number("Session length (minutes)",old==null?project.defaultMinutes():old.minutes(),15,240);var teacher=text("Instructor (optional text)",old==null?"":old.instructor(),false);
        form("Subject • Stage "+selectedStage+" / Group "+selectedGroup,List.of(name,type,count,sessions,minutes,teacher),()->{
            if(project.subjects.stream().anyMatch(s->s!=old&&s.stage()==selectedStage&&s.group().equals(selectedGroup)&&s.name().equalsIgnoreCase(name.value())&&s.type().equals(type.value())))return "This subject and type already exist for this group. Edit its sessions instead.";return "";
        },()->{var s=new SimpleProject.Subject(old==null?UUID.randomUUID().toString():old.id(),selectedStage,selectedGroup,name.value(),type.value(),count.value(),sessions.value(),minutes.value(),teacher.value());if(old==null)project.subjects.add(s);else project.subjects.set(project.subjects.indexOf(old),s);changed();render();});
    }
    void form(String title,List<SimpleFields.Field<?>> fields,Supplier<String> validate,Runnable commit){
        var dialog=new Dialog<ButtonType>();dialog.initOwner(stage);dialog.setTitle(title);dialog.setResizable(true);dialog.getDialogPane().getStylesheets().add(getClass().getResource("/studio.css").toExternalForm());
        var body=new VBox(10);body.getChildren().addAll(fields);body.setPadding(new Insets(16));var message=new Label();message.getStyleClass().add("error");message.setWrapText(true);body.getChildren().add(message);
        var scroll=new ScrollPane(body);scroll.setFitToWidth(true);scroll.setPrefViewportWidth(520);scroll.setPrefViewportHeight(Math.min(650,fields.size()*100+60));dialog.getDialogPane().setContent(scroll);dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL,ButtonType.OK);
        Runnable check=()->{boolean valid=true;for(var f:fields)valid=f.valid()&&valid;String error=validate.get();message.setText(error);dialog.getDialogPane().lookupButton(ButtonType.OK).setDisable(!valid||!error.isEmpty());};fields.forEach(f->f.watch(check));check.run();
        if(dialog.showAndWait().orElse(ButtonType.CANCEL)==ButtonType.OK)commit.run();
    }
    void days(){
        page.getChildren().add(note("Each selected day has its own full class periods. A shorter final remainder is unavailable. Subjects must fit within one class period; breaks cannot contain a class. For both scoring goals, scheduled breaks between two classes are deducted from their gap; any remaining waiting time counts as soft minutes."));
        var checks=new HBox(16);for(int i=0;i<project.days.size();i++){final int index=i;var d=project.days.get(i);var box=new CheckBox(d.name());box.setSelected(d.enabled());box.setTooltip(new Tooltip("Boolean: selected = teaching day. Default: Sunday through Thursday."));box.setOnAction(e->{project.days.set(index,new SimpleProject.Day(d.name(),box.isSelected(),d.start(),d.end(),d.classMinutes(),d.breaks()));changed();render();});checks.getChildren().add(box);}page.getChildren().add(checks);
        for(int i=0;i<project.days.size();i++)if(project.days.get(i).enabled())dayCard(i);
    }
    void dayCard(int index){
        var day=project.days.get(index);var card=new VBox(12);card.getStyleClass().add("card");var title=new Label(day.name());title.getStyleClass().add("section-title");
        var start=new SimpleFields.Time("Start time (24-hour)",day.start());var end=new SimpleFields.Time("End time (24-hour)",day.end());var length=number("Class length (minutes)",day.classMinutes(),15,240);var issue=new Label();issue.getStyleClass().add("error");issue.setWrapText(true);var preview=new FlowPane(6,6);
        Runnable update=()->{var old=project.days.get(index);var d=new SimpleProject.Day(old.name(),true,start.value(),end.value(),length.value(),old.breaks());project.days.set(index,d);String error=SimpleProject.dayError(d);issue.setText(error);preview.getChildren().clear();if(error.isEmpty())for(var p:SimpleProject.periods(d)){var block=new Label(p[2]+"–"+p[3]+"\n"+(p[4].equals("Teaching")?"Teaching":p[1]));block.getStyleClass().add(p[4].equals("Teaching")?"preview-teaching":"preview-break");preview.getChildren().add(block);}changed();};
        start.watch(update);end.watch(update);length.watch(update);
        card.getChildren().addAll(title,new HBox(8,start,end,length),issue);
        var breaks=new VBox(6);for(var b:day.breaks())breaks.getChildren().add(new HBox(8,new Label("Break   "+Data.clock(b.start())+"–"+Data.clock(b.end())),button("Remove break",()->{var old=project.days.get(index);var list=new ArrayList<>(old.breaks());list.remove(b);project.days.set(index,new SimpleProject.Day(old.name(),old.enabled(),old.start(),old.end(),old.classMinutes(),list));changed();render();})));
        card.getChildren().addAll(breaks,new HBox(8,button("Add break",()->breakDialog(index)),button("Copy to other days",()->copyDay(index))),note("Live preview · green = teaching · grey = break / short remainder"),preview);
        String error=SimpleProject.dayError(day);issue.setText(error);if(error.isEmpty())for(var p:SimpleProject.periods(day)){var block=new Label(p[2]+"–"+p[3]+"\n"+(p[4].equals("Teaching")?"Teaching":p[1]));block.getStyleClass().add(p[4].equals("Teaching")?"preview-teaching":"preview-break");preview.getChildren().add(block);}page.getChildren().add(card);
    }
    void breakDialog(int index){
        var d=project.days.get(index);if(!SimpleProject.dayError(d).isEmpty()){pageError.setText("Fix this day’s times before adding another break.");return;}var dialog=new Dialog<ButtonType>();dialog.initOwner(stage);dialog.setTitle("Add break • "+d.name());dialog.getDialogPane().getStylesheets().add(getClass().getResource("/studio.css").toExternalForm());
        var start=new SimpleFields.Time("Break start (24-hour)",Math.min(d.end()-1,d.start()+90));var end=new SimpleFields.Time("Break end (24-hour)",Math.min(d.end(),d.start()+120));var error=new Label();error.getStyleClass().add("error");error.setWrapText(true);
        dialog.getDialogPane().setContent(new VBox(8,start,end,error));dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL,ButtonType.OK);
        Runnable check=()->{var list=new ArrayList<>(d.breaks());list.add(new SimpleProject.Break(start.value(),end.value()));String message=SimpleProject.dayError(new SimpleProject.Day(d.name(),true,d.start(),d.end(),d.classMinutes(),list));error.setText(message);dialog.getDialogPane().lookupButton(ButtonType.OK).setDisable(!message.isEmpty());};start.watch(check);end.watch(check);check.run();
        if(dialog.showAndWait().orElse(ButtonType.CANCEL)==ButtonType.OK){var list=new ArrayList<>(d.breaks());list.add(new SimpleProject.Break(start.value(),end.value()));project.days.set(index,new SimpleProject.Day(d.name(),true,d.start(),d.end(),d.classMinutes(),list));changed();render();}
    }
    void copyDay(int source){
        var d=project.days.get(source);if(!SimpleProject.dayError(d).isEmpty()){pageError.setText("Fix "+d.name()+" before copying.");return;}
        var dialog=new Dialog<ButtonType>();dialog.initOwner(stage);dialog.setTitle("Copy "+d.name()+" times");var choices=new LinkedHashMap<Integer,CheckBox>();var body=new VBox(8,note("Replaces start, end, class length and breaks on selected days. Day selection stays unchanged."));
        for(int i=0;i<project.days.size();i++)if(i!=source){var c=new CheckBox(project.days.get(i).name());c.setTooltip(new Tooltip("Select this day to replace its time settings with "+d.name()+"."));choices.put(i,c);body.getChildren().add(c);}dialog.getDialogPane().setContent(body);dialog.getDialogPane().getStylesheets().add(getClass().getResource("/studio.css").toExternalForm());dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL,ButtonType.OK);
        if(dialog.showAndWait().orElse(ButtonType.CANCEL)==ButtonType.OK){choices.forEach((i,c)->{if(c.isSelected()){var target=project.days.get(i);project.days.set(i,new SimpleProject.Day(target.name(),target.enabled(),d.start(),d.end(),d.classMinutes(),d.breaks()));}});changed();render();}
    }
    void rooms(){
        var t=table(project.rooms,new String[]{"Name (text)","Type","Capacity (int)"},List.of(SimpleProject.Room::name,SimpleProject.Room::type,r->""+r.capacity()));
        page.getChildren().addAll(note("Theory uses a Classroom. Practical teaching uses a Lab. Each subject's own enrollment must fit."),new HBox(8,button("Add room",()->roomDialog(null)),button("Edit selected",()->{var r=t.getSelectionModel().getSelectedItem();if(r!=null)roomDialog(r);}),button("Remove selected",()->{var r=t.getSelectionModel().getSelectedItem();if(r!=null){project.rooms.remove(r);changed();render();}})),t);
    }
    void roomDialog(SimpleProject.Room old){
        var name=text("Room name (text)",old==null?"":old.name(),true);var type=choice("Type",old==null?"Classroom":old.type(),List.of("Classroom","Lab"));var seats=number("Capacity (students)",old==null?40:old.capacity(),1,SimpleProject.MAX_ENROLLMENT);
        form("Room",List.of(name,type,seats),()->project.rooms.stream().anyMatch(r->r!=old&&r.name().equalsIgnoreCase(name.value()))?"A room with this name already exists.":"",()->{var r=new SimpleProject.Room(name.value(),type.value(),seats.value());if(old==null)project.rooms.add(r);else project.rooms.set(project.rooms.indexOf(old),r);changed();render();});
    }
    void review(){
        page.getChildren().add(note("4 stages • "+project.groups.size()+" groups • "+project.subjects.size()+" subjects • "+project.subjects.stream().mapToInt(SimpleProject.Subject::sessions).sum()+" weekly sessions • "+project.rooms.size()+" rooms"));
        var issues=project.health();if(issues.stream().noneMatch(SimpleProject.Issue::error)){
            try{var m=new ProblemData(project.toData());for(String message:HealthReport.inspect(m))issues.add(new SimpleProject.Issue(true,message.startsWith("Instructor")?2:4,message));}catch(Exception ex){issues.add(new SimpleProject.Issue(true,2,ex.getMessage()));}
        }
        if(issues.isEmpty()){var ready=new Label("Ready to generate. All entered data passed the health checks.");ready.getStyleClass().add("success");page.getChildren().add(ready);}
        for(var issue:issues){var label=new Label(issue.message());label.setWrapText(true);label.getStyleClass().add(issue.error()?"error":"warning");var fix=new Hyperlink("Go fix it → "+steps[issue.step()]);fix.setOnAction(e->{step=issue.step();render();});var box=new VBox(8,label,fix);box.getStyleClass().add("card");page.getChildren().add(box);}
        page.getChildren().add(note("Hard: no room, instructor or group double-booking; room type/capacity; exact sessions; teaching times and breaks. Soft: choose least gap between subjects or earlier finishes of the day in Advanced search. Each gap deducts only the scheduled break minutes within it; earlier finishes also penalizes the first class starting after 09:00. Health checks are necessary checks; only the solver can establish full feasibility."));
    }
    Label searchWarning(){
        var warning=note("");try{var m=new ProblemData(project.toData());double log=m.choices.stream().mapToDouble(c->Math.log10(c.size())).sum();if(log>9&&method.value().equals("Brute-force"))warning.setText("Brute-force is only practical for tiny inputs.");}catch(Exception ignored){}warning.getStyleClass().add("warning");return warning;
    }
    void generation(){
        for(var pin:project.pins())page.getChildren().add(new HBox(8,note("Pinned: "+project.pinName(pin)),button("Remove pin",()->{project.unpin(pin);pinsChanged();})));
        var goal=choice("Finding best solution",project.optimizationGoal,List.of(ScheduleObjective.Goal.values()));
        solutionGoal=goal;
        goal.setId("solution-goal");goal.watch(()->{if(project.optimizationGoal!=goal.value()){project.optimizationGoal=goal.value();changed();runStatus.setText("Best solution goal changed — generate again");render();}});
        hybridPopulation=number("Initial population",project.hybridPopulation,2,SimpleProject.MAX_HYBRID_POPULATION);
        hybridPopulation.setId("hybrid-population");
        var selectedPopulation=hybridPopulation;
        selectedPopulation.watch(()->{
            if(project.hybridPopulation!=selectedPopulation.value()){
                project.hybridPopulation=selectedPopulation.value();
                searchPreferencesChanged();
            }
        });
        stopMode=choice("Hybrid stop condition",project.hybridStopping.mode(),List.of(HybridStopping.Mode.values()));
        stopGenerations=number("Generations without improvement",project.hybridStopping.generations(),1,1_000_000);
        stopSeconds=number("Seconds without improvement",(int)project.hybridStopping.seconds(),1,86400);
        var selectedStopMode=stopMode;var selectedGenerations=stopGenerations;var selectedSeconds=stopSeconds;
        stopMode.watch(()->{if(project.hybridStopping.mode()!=selectedStopMode.value()){project.hybridStopping=new HybridStopping(selectedStopMode.value(),project.hybridStopping.generations(),project.hybridStopping.seconds());searchPreferencesChanged();render();}});
        stopGenerations.watch(()->{if(project.hybridStopping.generations()!=selectedGenerations.value()){project.hybridStopping=new HybridStopping(project.hybridStopping.mode(),selectedGenerations.value(),project.hybridStopping.seconds());searchPreferencesChanged();}});
        stopSeconds.watch(()->{if(project.hybridStopping.seconds()!=selectedSeconds.value()){project.hybridStopping=new HybridStopping(project.hybridStopping.mode(),project.hybridStopping.generations(),selectedSeconds.value());searchPreferencesChanged();}});
        ((Spinner<Integer>)limit.input).getValueFactory().setValue(project.runtimeSeconds);
        boolean hybrid=method.value().equals("Hybrid");
        var stopping=new VBox(8,hybridPopulation,note("The starting population is a target number of candidate timetables. Each following round halves the target, down to two."),stopMode,stopGenerations,stopSeconds,note("Only the selected rule stops the hybrid: consecutive generations without a better best score, seconds since the last better best score, or total running time. An equal score does not reset either counter. Fixed running time continues even after an optimal score."));
        stopping.setVisible(hybrid);stopping.setManaged(hybrid);
        stopGenerations.setVisible(project.hybridStopping.mode()==HybridStopping.Mode.STAGNANT_GENERATIONS);stopGenerations.setManaged(stopGenerations.isVisible());
        stopSeconds.setVisible(project.hybridStopping.mode()==HybridStopping.Mode.STAGNANT_SECONDS);stopSeconds.setManaged(stopSeconds.isVisible());
        feasibleGenerator.setVisible(method.value().equals("Random"));feasibleGenerator.setManaged(feasibleGenerator.isVisible());
        limit.setVisible(!hybrid||project.hybridStopping.mode()==HybridStopping.Mode.TIME_LIMIT);limit.setManaged(limit.isVisible());
        var advanced=new TitledPane("Advanced • search method",new VBox(8,method,goal,stopping,note("Score = soft.hard (two integers). Fewer hard violations always wins; if equal, fewer soft minutes wins. Soft = sum of max(0, gap − scheduled break minutes within that gap) for consecutive classes per group/day. Breaks come from Days & times; a gap with no scheduled break has no free allowance. Earlier finishes adds max(0, first class start − 09:00) for each group/day with classes. Empty days add zero. The earliest eligible class start is 09:00."),searchWarning(),feasibleGenerator,note("Hybrid combines CP with genetic selection, crossover and mutation. Every run starts with fresh randomness, and equally good timetables can replace one another. The selected stop condition controls when the best result is returned. Brute-force is intended for tiny examples.")));advanced.setId("advanced-search");advanced.setExpanded(false);
        var start=button("Generate timetable",this::generate);start.getStyleClass().add("primary");start.setId("generate");var cancel=button("Cancel",()->{if(task!=null)task.cancel();});cancel.setId("cancel");cancel.setDisable(!busy);
        progress.setMaxWidth(Double.MAX_VALUE);progress.setPrefHeight(8);runStatus.setWrapText(true);
        var options=new VBox(8,limit,advanced,new HBox(8,start,cancel),progress);
        page.getChildren().add(options);
        page.getChildren().addAll(runStatus,timetable);
        if(result!=null)draw();else timetable.getChildren().setAll(note("Generate to see all four stages together. Stage, group, room and instructor filters will appear here."));
    }
    void generate(){
        if(busy)return;
        if(project.health().stream().anyMatch(SimpleProject.Issue::error)){step=5;render();pageError.setText("Fix the red errors before generating.");return;}
        autosave.stop();undoMove=null;SimpleProject snapshot=SimpleProject.parse(project.json());int seconds=limit.value();String selected=method.value();generatedMethod=selected;boolean constructive=feasibleGenerator.isSelected();
        busy=true;fileButtons.forEach(n->n.setDisable(true));sidebar.setDisable(true);back.setDisable(true);limit.setDisable(true);method.setDisable(true);solutionGoal.setDisable(true);hybridPopulation.setDisable(true);stopMode.setDisable(true);stopGenerations.setDisable(true);stopSeconds.setDisable(true);feasibleGenerator.setDisable(true);next.setDisable(true);
        root.lookup("#generate").setDisable(true);root.lookup("#cancel").setDisable(false);result=null;timetable.getChildren().setAll(note("Building a weekly timetable…"));long started=System.nanoTime();
        var timer=new Timeline(new KeyFrame(Duration.millis(150),e->{double elapsed=(System.nanoTime()-started)/1e9;progress.setProgress(live==null?-1:live.progress());runStatus.setText("Running "+selected+" • "+String.format(Locale.ROOT,"%.1f",elapsed)+" seconds • best score: "+(live==null||live.best==null?"searching":live.scoreText())+(live==null?"":live.stoppingSummary()));}));timer.setCycleCount(Animation.INDEFINITE);
        task=new Task<>(){protected SearchEngine call(){var search=snapshot.newSearch(seconds,this::isCancelled);search.feasibleGenerator=constructive;live=search;search.run(selected,selected.equals("Hybrid")?new ConstraintSearch():null);return search;}};
        Runnable finish=()->{timer.stop();busy=false;live=null;fileButtons.forEach(n->n.setDisable(false));sidebar.setDisable(false);back.setDisable(false);limit.setDisable(false);method.setDisable(false);solutionGoal.setDisable(false);hybridPopulation.setDisable(false);stopMode.setDisable(false);stopGenerations.setDisable(false);stopSeconds.setDisable(false);feasibleGenerator.setDisable(false);next.setDisable(false);root.lookup("#generate").setDisable(false);root.lookup("#cancel").setDisable(true);progress.setProgress(1);};
        task.setOnSucceeded(e->{
            finish.run();result=task.getValue();
            runStatus.setText(result.status+result.stoppingSummary()+" • nodes "+result.nodes+" • "+String.format(Locale.ROOT,"%.2f",(System.nanoTime()-started)/1e9)+" seconds"+(result.best==null?"":" • zero hard violations • score "+result.scoreText()+(selected.equals("Hybrid")?" • mutations "+result.mutations:"")));
            if(result.best!=null){
                if(!Evaluator.evaluate(result.m,result.best).valid())throw new IllegalStateException("Independent evaluator rejected a result");
                project.captureResult(result,selected);autosave.playFromStart();configureFilters();render();status.setText("Timetable generated • inputs "+(dirty?"not saved to a named file":"saved"));
            }else timetable.getChildren().setAll(note(result.status.equals("UNKNOWN")?selected+" tried "+result.evaluations+" evaluations / "+result.attempts+" attempts / "+result.nodes+" nodes in "+String.format(Locale.ROOT,"%.2f",result.elapsed())+" seconds. This does not mean no timetable exists. Try Hybrid with a longer stopping threshold, or a smaller input.":pinFailure(result.status)));
            task=null;
        });
        task.setOnCancelled(e->{finish.run();runStatus.setText("Cancelled. You can edit the inputs or generate again.");task=null;});
        task.setOnFailed(e->{finish.run();task.getException().printStackTrace();runStatus.setText("Could not generate: "+Objects.toString(task.getException().getMessage(),task.getException().toString())+pinFailure(""));task=null;});timer.play();new Thread(task,"simple-timetable-solver").start();
    }
    void configureFilters(){
        for(var f:List.of(stageFilter,groupFilter,roomFilter,teacherFilter)){f.setOnAction(null);f.getItems().setAll("All");f.setValue("All");}
        for(int s=1;s<=4;s++)stageFilter.getItems().add("Stage "+s);project.groups.forEach(g->groupFilter.getItems().add(g.id()));project.rooms.forEach(r->roomFilter.getItems().add(r.name()));project.subjects.stream().map(SimpleProject.Subject::instructor).filter(s->!s.isBlank()).distinct().sorted().forEach(teacherFilter.getItems()::add);
        for(var f:List.of(stageFilter,groupFilter,roomFilter,teacherFilter))f.setOnAction(e->draw());
    }
    boolean visible(int index){var e=result.m.events.get(index);var p=result.m.choices.get(index).get(result.best[index]);return (stageFilter.getValue().equals("All")||e.groups().stream().anyMatch(g->result.m.stages.get(g).equals(stageFilter.getValue())))&&(groupFilter.getValue().equals("All")||e.groups().contains(groupFilter.getValue()))&&(roomFilter.getValue().equals("All")||roomFilter.getValue().equals(p.room()))&&(teacherFilter.getValue().equals("All")||teacherFilter.getValue().equals(e.teacher()));}
    void draw(){
        if(result==null||result.best==null){timetable.getChildren().setAll(note("No feasible timetable found. Adjust the search settings and generate again."));visibleRows.clear();return;}timetable.getChildren().clear();visibleRows.clear();
        var filters=new FlowPane(16,10);String[] labels={"Stage","Group","Room","Instructor"};var fields=List.of(stageFilter,groupFilter,roomFilter,teacherFilter);
        for(int i=0;i<4;i++){var f=fields.get(i);f.setPrefWidth(170);f.setTooltip(new Tooltip("Filter by "+labels[i].toLowerCase(Locale.ROOT)+", or All."));filters.getChildren().add(new VBox(8,new Label(labels[i]),f));}
        timetable.getChildren().addAll(new Separator(),filters,new FlowPane(8,8,button("Export printable HTML",()->export(true)),button("Export visible CSV",()->export(false)),button("Print stage… / Save as PDF",()->printTimetable(true)),button("Print instructor… / Save as PDF",()->printTimetable(false)),undoButton()),note("Backgrounds: Theory = mint; Labs = teal, lime or orange; Breaks = grey. Left border: Stage 1 = forest green, Stage 2 = turquoise, Stage 3 = leaf green, Stage 4 = deep teal."));
        var grid=new GridPane();grid.setHgap(2);grid.setVgap(2);var headerGrid=new GridPane();var timeGrid=new GridPane();var corner=new GridPane();for(var g:List.of(headerGrid,timeGrid,corner)){g.setHgap(2);g.setVgap(2);}var shownGroups=result.m.stages.keySet().stream().filter(g->stageFilter.getValue().equals("All")||result.m.stages.get(g).equals(stageFilter.getValue())).filter(g->groupFilter.getValue().equals("All")||g.equals(groupFilter.getValue())).toList();
        var layout=new WeekGrid(result.m.data,result,shownGroups);double columnWidth=Math.min(240,Math.max(116,875.0/Math.max(1,shownGroups.size())));
        timeGrid.getColumnConstraints().addAll(new ColumnConstraints(85),new ColumnConstraints(60));corner.getColumnConstraints().addAll(new ColumnConstraints(85),new ColumnConstraints(60));
        for(int i=0;i<shownGroups.size();i++){grid.getColumnConstraints().add(new ColumnConstraints(columnWidth));headerGrid.getColumnConstraints().add(new ColumnConstraints(columnWidth));}
        int rows=layout.cells.stream().mapToInt(c->c.row()+c.height()).max().orElse(1)-1;
        for(int i=0;i<rows;i++){grid.getRowConstraints().add(new RowConstraints(56));timeGrid.getRowConstraints().add(new RowConstraints(56));}headerGrid.getRowConstraints().add(new RowConstraints(56));corner.getRowConstraints().add(new RowConstraints(56));
        for(var c:layout.cells){if(c.event()>=0&&!visible(c.event()))continue;var label=new Label(c.text());label.setWrapText(true);label.setMinWidth(0);label.setPrefWidth(c.col()==0?85:c.col()==1?60:columnWidth*c.width());label.setMaxSize(Double.MAX_VALUE,Double.MAX_VALUE);label.getStyleClass().addAll("sheet-cell",c.style());if(c.event()>=0){String group=result.m.events.get(c.event()).groups().iterator().next();String stageName=result.m.stages.get(group);label.getStyleClass().add("stage-"+stageName.replace("Stage ",""));}if(c.row()==0){if(c.col()<2)corner.add(label,c.col(),0,c.width(),1);else headerGrid.add(label,c.col()-2,0,c.width(),1);}
            else if(c.col()<2)timeGrid.add(label,c.col(),c.row()-1,c.width(),c.height());else grid.add(label,c.col()-2,c.row()-1,c.width(),c.height());if(c.event()>=0){String detail=c.text().replace('\n',' ');label.setTooltip(new Tooltip(detail));label.setOnMouseClicked(e->{if(e.getButton()==javafx.scene.input.MouseButton.PRIMARY){root.lookupAll(".sheet-selected").forEach(node->node.getStyleClass().remove("sheet-selected"));label.getStyleClass().add("sheet-selected");gridDetail.setText(detail);}});var pinMenu=new ContextMenu();var placement=project.placement(result,c.event());var pinItem=new MenuItem(project.isPinned(placement)?"Unpin":"Pin here");pinItem.setOnAction(e->{if(project.isPinned(placement))project.unpin(placement);else project.pin(placement);pinsChanged();});var moveItem=new MenuItem("Move…");moveItem.setDisable(project.isPinned(placement));moveItem.setOnAction(e->moveDialog(c.event()));pinMenu.getItems().addAll(pinItem,moveItem);label.setOnContextMenuRequested(e->pinMenu.show(label,e.getScreenX(),e.getScreenY()));}}
        var scroll=new ScrollPane(grid);scroll.setPrefViewportHeight(430);scroll.setMinHeight(260);scroll.setPannable(true);var headerScroll=new ScrollPane(headerGrid);headerScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);headerScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);headerScroll.setPrefHeight(58);headerScroll.setMinHeight(58);
        var timeScroll=new ScrollPane(timeGrid);timeScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);timeScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);timeScroll.setPrefWidth(149);timeScroll.setMinWidth(149);
        headerScroll.hvalueProperty().bindBidirectional(scroll.hvalueProperty());timeScroll.vvalueProperty().bindBidirectional(scroll.vvalueProperty());
        scroll.viewportBoundsProperty().addListener((o,a,b)->{timeScroll.setMaxHeight(b.getHeight()+2);headerScroll.setMaxWidth(b.getWidth()+2);});
        var frozen=new GridPane();frozen.add(corner,0,0);frozen.add(headerScroll,1,0);frozen.add(timeScroll,0,1);frozen.add(scroll,1,1);GridPane.setHgrow(scroll,Priority.ALWAYS);GridPane.setVgrow(scroll,Priority.ALWAYS);timetable.getChildren().addAll(frozen,gridDetail);
        for(int i=0;i<result.best.length;i++)if(visible(i)){var e=result.m.events.get(i);var p=result.m.choices.get(i).get(result.best[i]);visibleRows.add(new String[]{p.day(),Data.clock(p.start()),Data.clock(p.start()+e.span()),e.name(),e.teacher().startsWith("__unassigned_")?"":e.teacher(),String.join(",",e.groups()),p.room()});}
        var parts=ScheduleObjective.parts(result.m,result.best);timetable.getChildren().add(note(ScheduleObjective.goal(result.m)+" • extra gaps: "+parts.gaps()+" minutes • starts after 09:00: "+parts.starts()+" minutes • score (soft.hard): "+result.scoreText()+". Fewer hard violations wins first, then fewer soft minutes. A feasible result is not necessarily optimal."));
    }
    String pinFailure(String message){return message+(project.pins().isEmpty()?"":" • Check pinned classes: "+project.pins().stream().map(project::pinName).collect(java.util.stream.Collectors.joining("; ")));}
    String currentMethod(){return project.result==null?generatedMethod:project.result.method();}
    void pinsChanged(){
        dirty=true;undoMove=null;
        if(result!=null&&result.best!=null){String selected=currentMethod();project.captureResult(result,selected);result=project.restoreResult();if(result!=null){configureFilters();runStatus.setText("Pins updated • score "+result.scoreText()+" • zero hard violations");}else{project.result=null;runStatus.setText("Pins changed - regenerate");}}
        else project.result=null;autosave.playFromStart();render();
    }
    void instructors(){
        var dialog=new Dialog<ButtonType>();dialog.initOwner(stage);dialog.setTitle("Instructors — days they cannot teach");dialog.getDialogPane().getStylesheets().add(getClass().getResource("/studio.css").toExternalForm());var body=new VBox(8,note("Tick days OFF. This applies to every subject with the same instructor name."));var checks=new LinkedHashMap<String,List<CheckBox>>();
        for(String teacher:project.subjects.stream().map(s->s.instructor().trim()).filter(s->!s.isBlank()).distinct().sorted().toList()){var row=new FlowPane(8,8);row.getChildren().add(new Label(teacher));var values=new ArrayList<CheckBox>();for(String day:SimpleProject.WEEK){var box=new CheckBox(day);box.setSelected(project.off(teacher).contains(day));values.add(box);row.getChildren().add(box);}checks.put(teacher,values);body.getChildren().add(row);}var scroll=new ScrollPane(body);scroll.setFitToWidth(true);scroll.setPrefViewportHeight(420);scroll.setPrefViewportWidth(720);dialog.getDialogPane().setContent(scroll);dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL,ButtonType.OK);
        if(dialog.showAndWait().orElse(ButtonType.CANCEL)==ButtonType.OK){var off=new LinkedHashMap<String,List<String>>();checks.forEach((teacher,boxes)->{var days=new ArrayList<String>();for(int i=0;i<boxes.size();i++)if(boxes.get(i).isSelected())days.add(SimpleProject.WEEK.get(i));if(!days.isEmpty())off.put(teacher,days);});project.daysOff=off.isEmpty()?null:off;changed();render();}
    }
    void printTimetable(boolean byStage){
        if(result==null||result.best==null)return;var sections=new TreeSet<String>();for(var e:result.m.events)if(byStage){for(String g:e.groups())sections.add(result.m.stages.get(g));}else sections.add(e.teacher());var choices=new ArrayList<String>();choices.add("All");choices.addAll(sections);var dialog=new ChoiceDialog<String>("All",choices);dialog.initOwner(stage);dialog.setTitle("Print / Save as PDF");dialog.setHeaderText(byStage?"Choose stage(s)":"Choose instructor(s)");dialog.showAndWait().ifPresent(selected->{try{var file=Files.createTempFile("UniPlan-print-", ".html");Files.writeString(file,SimpleExport.printHtml(result,byStage,selected));getHostServices().showDocument(file.toUri().toString());status.setText("Print HTML opened. In your browser choose Print → Save as PDF.");}catch(Exception ex){showError(ex);}});
    }
    void jointDialog(SimpleProject.Subject selected){
        String source=project.canonical(selected.id());var subject=project.subject(source);var choices=new LinkedHashMap<String,String>();choices.put("Separate classes (remove joint)","");for(var target:project.subjects)if(project.compatible(subject,target))choices.put("Stage "+target.stage()+" / Group "+target.group()+" — "+target.students()+" students",target.id());var dialog=new ChoiceDialog<String>(choices.keySet().iterator().next(),choices.keySet());dialog.initOwner(stage);dialog.setTitle("Joint with group…");dialog.setHeaderText(subject.name()+" — matching name, sessions, length and instructor required");dialog.showAndWait().ifPresent(choice->{try{String target=choices.get(choice);if(target.isEmpty()){if(project.jointWith!=null)project.jointWith.remove(source);}else project.join(source,target);changed();render();status.setText("Joint classes updated. Review checks combined capacity.");}catch(Exception ex){showError(ex);}});
    }
    void moveDialog(int event){
        var original=result;var dialog=new Dialog<Integer>();dialog.initOwner(stage);dialog.setTitle("Move class — conflict-free slots only");dialog.setHeaderText(original.m.events.get(event).name());var list=new ListView<Integer>();list.setPrefSize(560,320);list.setPlaceholder(new Label("Checking all eligible placements…"));list.setCellFactory(view->new ListCell<>(){protected void updateItem(Integer index,boolean empty){super.updateItem(index,empty);if(empty||index==null)setText(null);else{var p=original.m.choices.get(event).get(index);setText(p.day()+" "+Data.clock(p.start())+"–"+Data.clock(p.start()+original.m.events.get(event).span())+" / "+p.room());}}});dialog.getDialogPane().setContent(list);dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL,ButtonType.OK);dialog.getDialogPane().getStylesheets().add(getClass().getResource("/studio.css").toExternalForm());dialog.getDialogPane().lookupButton(ButtonType.OK).disableProperty().bind(list.getSelectionModel().selectedItemProperty().isNull());dialog.setResultConverter(type->type==ButtonType.OK?list.getSelectionModel().getSelectedItem():null);
        var check=new Task<List<Integer>>(){protected List<Integer> call(){return project.moveOptions(original,event);}};check.setOnSucceeded(e->{list.getItems().setAll(check.getValue());list.setPlaceholder(new Label("No conflict-free alternative slots. Unpin this class first if pinned."));});check.setOnFailed(e->list.setPlaceholder(new Label("Could not check alternatives: "+check.getException().getMessage())));dialog.setOnHidden(e->check.cancel());var thread=new Thread(check,"check-class-moves");thread.setDaemon(true);thread.start();dialog.showAndWait().ifPresent(choice->{try{String selected=currentMethod();var moved=project.move(original,event,choice);undoMove=original;undoMethod=selected;result=moved;project.captureResult(result,"Manual");dirty=true;autosave.playFromStart();runStatus.setText("Moved manually • score "+result.scoreText()+" • zero hard violations");render();}catch(Exception ex){showError(ex);}});
    }
    Button undoButton(){var undo=button("Undo last move",()->{if(undoMove==null)return;result=undoMove;undoMove=null;project.captureResult(result,undoMethod);dirty=true;autosave.playFromStart();runStatus.setText("Move undone • score "+result.scoreText());render();});undo.setDisable(undoMove==null);return undo;}
    void export(boolean html){var chooser=new FileChooser();chooser.setInitialFileName(html?"Department-Timetable.html":"Department-Timetable.csv");var f=chooser.showSaveDialog(stage);if(f==null)return;try{if(html)SheetExport.save(result.m.data,result,f.toPath());else SimpleExport.csv(f.toPath(),visibleRows);status.setText("Exported "+f.getName());}catch(Exception ex){showError(ex);}}
    public static void main(String[] args){launch(args);}
}
