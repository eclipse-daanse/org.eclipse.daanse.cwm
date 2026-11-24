# org.eclipse.daanse.cwm.model.daanse.validation

What a validator of a model found, in one form, so that whoever reads the findings of several
models takes them all alike: every validator of a Daanse model is to report in this one form. The
module is plain Java on EMF and holds no model of its own.

| Type | Role |
| --- | --- |
| `Finding` | one result of a check: the module and the rule (`spatial/SP2`), the severity (`ERROR`, `WARNING`, `INFO`), the element it is about and what is wrong, for a person |
| `Rule` | a rule stated as data: module, code, severity and the sentence of what it asks of a model; `at(element, message)` makes a finding of it |
| `Findings` | what the EMF check of a model (`Diagnostician`) says, as findings: a constraint named by its rule is a finding of that rule, any other diagnostic one of the module `ecore`; the findings as lines of rule and message |

A rule is numbered within its module; module and rule together name it among all models. A rule
weighs the same wherever it is checked: a check whose finding weighs otherwise is a rule of its
own, with a code of its own. A validator keeps each rule as a constant and all of them in a list,
so that whoever weighs the findings reads the rules from the validator:

```java
static final Rule SP2 = Rule.error("spatial", "SP2", "An attribute is named once, by one holding.");

List<Finding> found = new ArrayList<>(Findings.of("spatial", Diagnostician.INSTANCE.validate(model)));
found.add(SP2.at(column, "the column is named by two holdings"));
Findings.errorLines(found);     // [SP2 the column is named by two holdings]
```

## Relations

- Builds on EMF only (`EObject`, `Diagnostic`); knows no CWM class.
- The validators of `spatial` and `resource.spatial.feature` report in it.
