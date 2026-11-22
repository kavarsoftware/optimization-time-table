package edu.timetable;
import javafx.application.Platform;
import javafx.animation.PauseTransition;
import javafx.util.Duration;
import java.nio.file.*;
import java.util.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
/** Deterministic UI integration harness, invoked only with -Dsimple.smoke=true. */
final class SimpleSmoke {
    static long started;static boolean finished;
    static void run(SimpleApp app){
        try{
            Files.createDirectories(Path.of("results"));
            if(app.project.subjects.size()!=40||app.project.groups.size()!=8||app.project.rooms.size()!=14)throw new AssertionError("Startup must contain the full default data");
            ((javafx.scene.control.Button)app.root.lookup("#new-project")).fire();app.autosave.stop();app.dirty=false;
            if(app.project.subjects.size()!=40||app.project.groups.size()!=8||app.project.rooms.size()!=14)throw new AssertionError("New must preserve the full default data");
            System.out.println("PASS startup and New: 40 default subjects, 8 groups, 14 rooms");
            for(int i=0;i<7;i++){app.step=i;app.render();if(i<6&&!app.validatePage())throw new AssertionError("Wizard page "+i+": "+app.pageError.getText());if(i==0)snapshot(app,"01-university.png");if(i==3)snapshot(app,"02-daily-times.png");}
            if(!((javafx.scene.control.ComboBox<?>)app.method.input).getItems().equals(List.of("Hybrid","Random","Brute-force")))throw new AssertionError("Only three search methods should be offered");
            if(app.root.lookup("#generate-options")!=null)throw new AssertionError("Three-options button should be removed");
            if(!app.method.value().equals("Hybrid"))throw new AssertionError("Hybrid should be the default");
            ((javafx.scene.control.ComboBox<ScheduleObjective.Goal>)app.solutionGoal.input).setValue(ScheduleObjective.Goal.EARLIER_FINISH);
            if(app.project.optimizationGoal!=ScheduleObjective.Goal.EARLIER_FINISH)throw new AssertionError("Earlier-finish goal not applied");
            ((javafx.scene.control.ComboBox<ScheduleObjective.Goal>)app.solutionGoal.input).setValue(ScheduleObjective.Goal.FEWER_GAPS);
            if(app.project.optimizationGoal!=ScheduleObjective.Goal.FEWER_GAPS)throw new AssertionError("Fewer-gaps goal not applied");
            if(app.project.hybridStopping.generations()!=10||app.project.hybridStopping.seconds()!=15)throw new AssertionError("Hybrid stopping defaults");
            ((javafx.scene.control.ComboBox<HybridStopping.Mode>)app.stopMode.input).setValue(HybridStopping.Mode.STAGNANT_SECONDS);
            if(!app.stopSeconds.isVisible()||app.limit.isVisible())throw new AssertionError("Inactivity controls");
            ((javafx.scene.control.ComboBox<HybridStopping.Mode>)app.stopMode.input).setValue(HybridStopping.Mode.TIME_LIMIT);
            ((javafx.scene.control.Spinner<Integer>)app.limit.input).getValueFactory().setValue(15);
            if(!app.limit.isVisible()||app.stopGenerations.isVisible()||app.stopSeconds.isVisible())throw new AssertionError("Fixed-time controls");
            var advanced=(javafx.scene.control.TitledPane)app.root.lookup("#advanced-search");advanced.setAnimated(false);advanced.setExpanded(true);
            snapshot(app,"05-advanced-search.png");
            if(app.root.lookupAll(".label").stream().anyMatch(n->n instanceof javafx.scene.control.Label label&&label.getText().equals("Workers")))throw new AssertionError("Workers control should be removed");
            advanced.setExpanded(false);app.autosave.stop();app.dirty=false;
            System.out.println("PASS seven wizard screens, default hybrid, both goal controls, all hybrid stop controls, five independent days");
            String smokeMethod=System.getProperty("simple.smoke.method","Hybrid");
            ((javafx.scene.control.ComboBox<String>)app.method.input).setValue(smokeMethod);app.generate();var cancel=new PauseTransition(Duration.millis(250));cancel.setOnFinished(e->{((javafx.scene.control.Button)app.root.lookup("#cancel")).fire();afterCancel(app,0);});cancel.play();
        }catch(Throwable ex){fail(ex);}
    }
    static void afterCancel(SimpleApp app,int checks){
        var pause=new PauseTransition(Duration.millis(100));pause.setOnFinished(e->{try{
            if(app.busy){if(checks>50)throw new AssertionError("Cancellation did not release the UI");afterCancel(app,checks+1);return;}
            if(!app.runStatus.getText().startsWith("Cancelled"))throw new AssertionError("Cancel feedback missing");
            System.out.println("PASS Cancel button and responsive controls");started=System.nanoTime();app.generate();poll(app);
        }catch(Throwable ex){fail(ex);}});pause.play();
    }
    static void poll(SimpleApp app){
        var wait=new PauseTransition(Duration.millis(100));wait.setOnFinished(e->{try{
            if(app.busy){if((System.nanoTime()-started)/1e9>32)throw new AssertionError("Demo exceeded 32-second guard");poll(app);return;}
            if(app.result==null||app.result.best==null)throw new AssertionError("No feasible demo: "+app.runStatus.getText());
            var evaluation=Evaluator.evaluate(app.result.m,app.result.best);if(!evaluation.valid())throw new AssertionError(evaluation.violations());
            double elapsed=(System.nanoTime()-started)/1e9;if(app.method.value().equals("Hybrid")&&elapsed<15)throw new AssertionError("Fixed-time mode stopped early");if(elapsed>=30)throw new AssertionError("Demo exceeded requested 30 seconds: "+elapsed);
            if(app.result.m.events.size()!=64)throw new AssertionError("Expected 64 sessions");
            if(!app.runStatus.getText().contains(app.result.scoreText()))throw new AssertionError("soft.hard score missing from status");
            app.draw();snapshot(app,"03-timetable-all-stages.png");
            app.stageFilter.setValue("Stage 4");if(app.visibleRows.size()!=16)throw new AssertionError("Stage filter");snapshot(app,"04-stage-four.png");
            app.groupFilter.setValue("4 A");if(app.visibleRows.size()!=8)throw new AssertionError("Group filter");
            SimpleExport.csv(Path.of("results/demo-stage4-A.csv"),app.visibleRows);
            String teacher=app.visibleRows.getFirst()[4];app.teacherFilter.setValue(teacher);if(app.visibleRows.isEmpty()||app.visibleRows.stream().anyMatch(r->!r[4].equals(teacher)))throw new AssertionError("Instructor filter");
            String room=app.visibleRows.getFirst()[6];app.roomFilter.setValue(room);if(app.visibleRows.isEmpty()||app.visibleRows.stream().anyMatch(r->!r[6].equals(room)))throw new AssertionError("Room filter");SheetExport.save(app.result.m.data,app.result,Path.of("results/demo-timetable.html"));
            app.project.save(Path.of("results/smoke-demo.json"));if(SimpleProject.load(Path.of("results/smoke-demo.json")).subjects.size()!=40)throw new AssertionError("Saved demo");
            String output=String.format(Locale.ROOT,"PASS demo: 4 stages, 8 groups, 64 sessions / 40 subjects, 10 classrooms, 4 labs%nstatus=%s%nobjective=%s%nwall_seconds=%.6f%nhard_violations=%d%nPASS stage/group/room/instructor filters, JSON round trip, HTML and CSV export%n",app.result.status,app.result.scoreText(),elapsed,evaluation.violations().size());
            output+="method="+app.method.value()+" mutations="+app.result.mutations+"\n";
            Files.writeString(Path.of("results/demo-verification.txt"),output);System.out.print(output);finished=true;app.stage.close();Platform.exit();
        }catch(Throwable ex){fail(ex);}});wait.play();
    }
    static void snapshot(SimpleApp app,String name)throws Exception{
        app.root.applyCss();app.root.layout();var image=app.stage.getScene().snapshot(null);var png=new BufferedImage((int)image.getWidth(),(int)image.getHeight(),BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<png.getHeight();y++)for(int x=0;x<png.getWidth();x++)png.setRGB(x,y,image.getPixelReader().getArgb(x,y));ImageIO.write(png,"png",Path.of("results",name).toFile());
    }
    static void fail(Throwable ex){ex.printStackTrace();Platform.exit();System.exit(1);}
}
