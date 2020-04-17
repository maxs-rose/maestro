parser grammar MablParser;

@header {
  //  package org.intocps.maestro.ast;
}

options { tokenVocab=MablLexer;}


compilationUnit
    : moduleDeclaration*  simulationSpecification? EOF
    ;

moduleDeclaration
    : MODULE IDENTIFIER ('{' functionDeclaration* '}')
    ;

functionDeclaration
    : ret=typeType IDENTIFIER formalParameters ';'
    ;

formalParameters
    : '(' formalParameterList? ')'
    ;

formalParameterList
    : formalParameter (',' formalParameter)*
    ;

formalParameter
    : typeType IDENTIFIER
    ;

simulationSpecification
    : SIMULATION (IMPORT imports+=IDENTIFIER ';')* block
    ;

block
    : '{' statement* '}'
    ;

//blockStatement
//    : localVariableDecleration ';'
//    | statement
//    ;

statement
    : blockLabel=block                              #blockStm
    | assignment                                    #assignmentStm
    | type=typeType var=variableDeclarator ';'      #localVariable
    | IF parExpression then=statement (ELSE el=statement)?  #if
 //   | FOR '(' forControl ')' statement
    | WHILE parExpression statement                 #while
    | statementExpression=expression  ';'           #expressionStatement
    | EXTERNAL methodCall  ';'                      #methodExternalCallStm
    | SEMI                                          #semi
    | OBSERVABLE                                    #observable
    ;


assignment
    : IDENTIFIER ASSIGN expression ';'
    ;


expression
    : IDENTIFIER                                            # identifierExp
    | literal                                               # literalExp
    | LOAD LPAREN expressionList? RPAREN                    # loadExp
    | UNLOAD LPAREN IDENTIFIER RPAREN                       # unloadExp
    | root=IDENTIFIER bop='.'
        (
             second= IDENTIFIER
            | methodCall
        )                                                   # dotExp
    | expression bop=('+'|'-') expression                   # plusMinusExp
    | expression bop=('<=' | '>=' | '>' | '<') expression   # numberComparizonExp
    | expression bop=('==' | '!=') expression               # equalityExp
    | <assoc=right> expression
          bop=('=' | '+=' | '-=' | '*=' | '/=' | '&=' | '|=' | '^=' | '>>=' | '>>>=' | '<<=' | '%=')
          expression                                        # updateExp
    ;

parExpression
    : '(' expression ')'
    ;

expressionList
    : expression (',' expression)*
    ;

methodCall
    : IDENTIFIER '(' expressionList? ')'
    ;



//localVariableDecleration
//    : type=typeType var=variableDeclarator ';'
//    ;

variableDeclarator
    : varid=variableDeclaratorId ('=' initializer=variableInitializer)?
    ;

variableDeclaratorId
    : IDENTIFIER ('[' DECIMAL_LITERAL ']')*

    ;

variableInitializer
    : arrayInitializer  #arrayInit
    | expression        #expInit
    ;

arrayInitializer
    : '{' (init+=expression (',' init+=expression)* (',')? )? '}'
    ;

typeType
    : (primitiveType | IDENTIFIER) (arrays+='[' ']')*
    ;

primitiveType
    : REAL      #realType
    | UINT      #unitType
    | INT       #intType
    | STRING    #stringType
    | BOOL      #boolType
    ;

literal
    : STRING_LITERAL
    | BOOL_LITERAL
    | DECIMAL_LITERAL
    | FLOAT_LITERAL
    ;

//Module<FMI2> FMUA = Load("FMI2", "path/to/FMUA.fmu")
//Module<FMI2> FMUB = Load("FMI2", "path/to/FMUB.fmu")
