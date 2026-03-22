package metrics.maintainability.cyclomatic_complexity;

public class Decision2 {
    void func1(){
        int i = 1;
        if(i){
           if(i) return;

           return;
        } else if(i) return;

        return;
    }
}
