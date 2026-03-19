package metrics.maintainability.cyclomatic_complexity;

public class Conditional2 {
    void func1(){
        int i = 1;
        int x = 1;
        boolean r = i == 1 && x == 2;

        if(r){
          
        } else if (i == 2 || x == 2){

        }
        return;
    }
}
