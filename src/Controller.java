import javax.script.Bindings;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.LinkedHashMap;

public class Controller {
    private final Class<?> model;
    private final Object modelCopy;
    private final Field[] fields;
    private String[] years;
    private int columns;
    private final LinkedHashMap<String, double[]> modelFields;
    private final ScriptEngine engine;


    public Controller(String modelName) throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {

        model = Class.forName("models." + modelName);
        fields = model.getDeclaredFields();
        modelCopy = model.getConstructor().newInstance();
        modelFields = new LinkedHashMap<>();
        ScriptEngineManager engineManager = new ScriptEngineManager();
        this.engine = engineManager.getEngineByName("groovy");


        for (Field field : fields) {
            field.setAccessible(true);
        }
    }

    public void readDataFrom(String fname) throws IllegalAccessException {
        try (BufferedReader bufferedReader = new BufferedReader(new FileReader(fname))) {
            String yearLine = bufferedReader.readLine();
            if (yearLine == null) {
                throw new IOException("File " + fname + " is empty");
            }
            years = yearLine.split("\\s+");
            columns = years.length - 1;
            fields[0].set(modelCopy,columns);

            String line;
            while ((line = bufferedReader.readLine()) != null) {
                String[] parts = line.split("\\s+");
                String field = parts[0];

                if (parts.length < 2) {
                    double[] defaultValues = new double[columns];
                    Arrays.fill(defaultValues, 0.0);
                    modelFields.put(parts[0], defaultValues);
                }

                double[] values = new double[columns];
                Arrays.fill(values, 0);
                for (int i = 1; i < parts.length; i++) {
                    try {
                        values[i - 1] = Double.parseDouble(parts[i]);
                    } catch (NumberFormatException ignored) {

                    }
                }
                double prev = 0;
                for (int i = 0; i < values.length; i++) {
                    if (values[i] != 0) {
                        prev = values[i];
                    } else if (prev != 0) {
                        values[i] = prev;
                    } else {
                        values[i] = 0.0;
                    }
                }
                modelFields.put(field, values);

            }
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    public void runModel() throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Method run = modelCopy.getClass().getMethod("run");
        reassignValues();
        run.invoke(modelCopy);
        reassignValues();
    }

    public void reassignValues() throws IllegalAccessException {
        for (Field field : fields) {
            String fieldName = field.getName();
            if (!modelFields.containsKey(fieldName)) {
                Object value = field.get(modelCopy);
                if (value instanceof double[]) {
                    modelFields.put(fieldName, (double[]) value);
                }
            }
        }
        for (Field field : fields) {
            String fieldName = field.getName();
            if (modelFields.containsKey(fieldName)) {
                double[] values = modelFields.get(fieldName);
                field.set(modelCopy, values);
            }
        }
    }


    public void runScriptFromFile(String fname) throws IllegalAccessException {
        try (BufferedReader bufferedReader = new BufferedReader(new FileReader(fname))) {
            StringBuilder stringBuilder = new StringBuilder();
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                stringBuilder.append(line).append("\n");
            }
            runScript((stringBuilder.toString()));
            reassignValues();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void runScript(String script) {
        if (script == null || script.trim().isEmpty()) {
            System.err.println("Script is empty or null.");
            return;
        }
        try {
            for (var entry:modelFields.entrySet()){
              engine.put(entry.getKey(),entry.getValue());
            }
            engine.put("LL",columns);
            engine.eval(script);

            Bindings bindings = engine.getBindings(javax.script.ScriptContext.ENGINE_SCOPE);
            for (String key : bindings.keySet()) {
                Object value = bindings.get(key);
                if (value instanceof double[] && !modelFields.containsKey(key)) {
                    modelFields.put(key, (double[]) value);
                }
            }
            reassignValues();
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
        }
    }


    public String getResultsAsTsv() {
        if (modelFields == null || modelFields.isEmpty()) {
            return "No results.";
        }

        StringBuilder tsvBuilder = new StringBuilder();

        if (years != null ) {
            for (String year : years) {
                tsvBuilder.append(year).append("\t");
            }
        }
        tsvBuilder.append("\n");

        for (var entry : modelFields.entrySet()) {
            tsvBuilder.append(entry.getKey());
            double[] values = entry.getValue();
            if (values != null) {
                for (Double value : values) {
                    tsvBuilder.append("\t").append(value != null ? value : "");
                }
            } else {
                tsvBuilder.append("\t".repeat(Math.max(0, years.length)));
            }
            tsvBuilder.append("\n");
        }

        return tsvBuilder.toString();
    }

}
