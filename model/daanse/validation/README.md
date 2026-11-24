# org.eclipse.daanse.cwm.model.daanse.validation

What a validator of a model found, in one form, so that whoever reads the findings of several
models takes them all alike: every validator of a Daanse model is to report in this one form. The
module is plain Java on EMF and holds no model of its own.

| Type | Role |
| --- | --- |
| `Finding` | one result of a check: the module and the rule (`spatial/SP2`), the severity (`ERROR`, `WARNING`, `INFO`), the element it is about and what is wrong, for a person |
| `Rule` | a rule stated as data: module, code, severity and the sentence of what it asks of a model; `at(element, message)` makes a finding of it |
| `Findings` | what the EMF check of a model (`Diagnostician`) says, as findings: a constraint named by its rule is a finding of that rule, any other diagnostic one of the module `ecore`; the findings of the invariants a metamodel states (`ofInvariants`); the findings as lines of rule and message |

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

## Rules in the metamodel

A metamodel states a rule as an invariant of the class it is about, named by the code of the rule,
and beside it, in the annotation `<namespace URI of its package>/constraints` of that class, what
the rule weighs and says: `SP23.level` (`error`, `warning` or `info`), `SP23.description` (the
sentence of the rule) and `SP23.name` (a short name). The EMF check reports every invariant that is
not kept as an error and names the element by a label; `Findings` makes of it a finding of the rule
that weighs as its level says and says the label and the sentence. An invariant whose delegate
fails to evaluate it is a finding of its rule as well, with the message of the failure, so that it
does not pass unseen.

`Findings.ofInvariants(module, ePackage, elements, label)` checks the elements of the classes of a
package with the package's validator and keeps the findings of the invariants the package states; a
required feature left unset, a dangling reference or a constraint a class inherits from another
package is left to other checks. A validator gives these findings and adds those of the rules no
invariant can state. Where the invariants are written in OCL, the delegate is installed first: a
plain Java program has no plugin registry that does so.

## Relations

- Builds on EMF only (`EObject`, `Diagnostic`, `EValidator`); knows no CWM class and no OCL.
- The validators of `spatial` and `resource.spatial.feature` report in it.
