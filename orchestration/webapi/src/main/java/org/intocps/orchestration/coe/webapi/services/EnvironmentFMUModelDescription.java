package org.intocps.orchestration.coe.webapi.services;

import org.intocps.orchestration.coe.modeldefinition.ModelDescription;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class EnvironmentFMUModelDescription {
    public static String CreateEnvironmentFMUModelDescription(List<ModelDescription.ScalarVariable> inputs, List<ModelDescription.ScalarVariable> outputs, String fmuName) {
        return "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?>\n" +
                "<fmiModelDescription \tfmiVersion=\"2.0\"\n" +
                "\t\t\t\t\t\tmodelName=\"" + fmuName + "\"\n" +
                "\t\t\t\t\t\tguid=\"{abb4bff1-d423-4e02-90d9-011f519869ff}\"\n" +
                "\t\t\t\t\t\tvariableNamingConvention=\"flat\"\n" +
                "\t\t\t\t\t\tnumberOfEventIndicators=\"0\">\n" +
                "\t<CoSimulation \tmodelIdentifier=\"" + fmuName + "\"\n" +
                "\t\t\t\t\tneedsExecutionTool=\"false\"\n" +
                "\t\t\t\t\tcanHandleVariableCommunicationStepSize=\"true\"\n" +
                "\t\t\t\t\tcanInterpolateInputs=\"false\"\n" +
                "\t\t\t\t\tmaxOutputDerivativeOrder=\"0\"\n" +
                "\t\t\t\t\tcanRunAsynchronuously=\"false\"\n" +
                "\t\t\t\t\tcanBeInstantiatedOnlyOncePerProcess=\"true\"\n" +
                "\t\t\t\t\tcanNotUseMemoryManagementFunctions=\"true\"\n" +
                "\t\t\t\t\tcanGetAndSetFMUstate=\"false\"\n" +
                "\t\t\t\t\tcanSerializeFMUstate=\"false\"\n" +
                "\t\t\t\t\tprovidesDirectionalDerivative=\"false\">\n" +
                "\t\t\t\t\t\n" +
                "\t\t</CoSimulation>\n" +
                "\n" +
                "\t<ModelVariables>\n" +
                (outputs != null ? createScalarVariables(outputs) : "") +
                (inputs != null ? createScalarVariables(inputs) : "") +
//                "\t\t<ScalarVariable name=\"backwardRotate\" valueReference=\"1\" causality=\"parameter\" variability=\"fixed\"  initial=\"exact\" ><Real start=\"0.1\" /></ScalarVariable>\n" +
//                "    \n" +
//                "\t\t<ScalarVariable name=\"forwardRotate\" valueReference=\"2\" causality=\"parameter\" variability=\"fixed\"  initial=\"exact\" ><Real start=\"0.5\" /></ScalarVariable>\n" +
//                "    \n" +
//                "\t\t<ScalarVariable name=\"forwardSpeed\" valueReference=\"3\" causality=\"parameter\" variability=\"fixed\"  initial=\"exact\" ><Real start=\"0.4\" /></ScalarVariable>\n" +
//                "    \n" +
//                "\t\t<ScalarVariable name=\"lfLeftVal\" valueReference=\"4\" causality=\"input\" variability=\"continuous\" ><Real start=\"0\" /></ScalarVariable>\n" +
//                "    \n" +
//                "\t\t<ScalarVariable name=\"lfRightVal\" valueReference=\"5\" causality=\"input\" variability=\"continuous\" ><Real start=\"0\" /></ScalarVariable>\n" +
//                "    \n" +
//                "\t\t<ScalarVariable name=\"servoLeftVal\" valueReference=\"6\" causality=\"output\" variability=\"continuous\" ><Real  /></ScalarVariable>\n" +
//                "    \n" +
//                "\t\t<ScalarVariable name=\"servoRightVal\" valueReference=\"7\" causality=\"output\" variability=\"continuous\" ><Real  /></ScalarVariable>\n" +
                "    </ModelVariables>\n" +
                "\n" +
                "\t<ModelStructure>\n" +
                "\t<Outputs>\n" +
                (outputs != null ? createEmptyDependencies(outputs.size(), 1) : "") +
//                "\t\t\t<Unknown index=\"6\"  dependencies=\"\"/>\n" +
//                "            \n" +
//                "\t\t\t<Unknown index=\"7\"  dependencies=\"\"/>\n" +
                "            \n" +
                "\t</Outputs>\n" +
                "\n" +
                "\t</ModelStructure>\n" +
                "</fmiModelDescription>\n";

    }

    public static String createEmptyDependencies(int size, int startIndex) {
        ArrayList<String> emptyDependencies = new ArrayList<>();
        for (int i = startIndex; i < startIndex + size; i++) {
            emptyDependencies.add(String.format("<Unknown index=\"%i\" dependencies=\"\"/>", i));
        }
        return String.join("\n\n", emptyDependencies);
    }

    public static String createScalarVariables(List<ModelDescription.ScalarVariable> scalarVariables) {
        String svsString = scalarVariables.stream().map(sv -> createScalarVariable(sv)).collect(Collectors.joining("\n\n"));
        return svsString;
    }

    /**
     * Create the XML representation of a Scalar Variable.
     * NOTE: SKIPS THE INITIAL FIELD
     *
     * @param sv
     * @return
     */
    public static String createScalarVariable(ModelDescription.ScalarVariable sv) {
        return String.format("<ScalarVariable " +
                        "name=\"%s\" " +
                        "valueReference=\"%s\" " +
                        "causality=\"%s\" " +
                        "variability=\"%s\">" +
                        "%s" +
                        "</ScalarVariable>",
                sv.name,
                sv.valueReference,
                causalityToString(sv.causality),
                variabilityToString(sv.variability),
                typeDefinitionToString(sv.type));


    }

    public static String typeDefinitionToString(ModelDescription.Type type) {
        StringBuilder typeDefinitionBuilder = new StringBuilder();
        typeDefinitionBuilder.append(String.format("<%s ", type.type.toString()));
        if (type.start != null) {
            typeDefinitionBuilder.append(String.format(" start=\"%s\"", type.start.toString()));
        }
        typeDefinitionBuilder.append("/>");
        return typeDefinitionBuilder.toString();
    }

    public static String variabilityToString(ModelDescription.Variability variability) {
        switch (variability) {
            case Constant:
                return "constant";
            case Fixed:
                return "fixed";
            case Tunable:
                return "tunable";
            case Discrete:
                return "discrete";
            case Continuous:
                return "continuous";
        }
        return null;
    }

    public static String causalityToString(ModelDescription.Causality causality) {
        switch (causality) {
            case Parameter:
                return "parameter";
            case CalculatedParameter:
                return "calculatedParameter";
            case Input:
                return "input";
            case Output:
                return "output";
            case Local:
                return "local";
            case Independent:
                return "independent";
        }
        return null;
    }
}
