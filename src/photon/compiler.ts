import _ from 'lodash';

interface Location {
  file: string;
  startLine: number;
  startColumn: number;
  endLine: number;
  endColumn: number;
}

interface Meta {
  location?: Location;
  defined?: boolean;
}

type Unknown   = {type: 'unknown',    meta?: Meta};
type Nothing   = {type: 'nothing',    meta?: Meta};
type Boolean   = {type: 'boolean',    meta?: Meta};
type Number    = {type: 'number',     meta?: Meta, value: number};
type String    = {type: 'string',     meta?: Meta, value: string};
type Struct    = {type: 'struct',     meta?: Meta, value: {[key: string]: Value}};
type Lambda    = {type: 'lambda',     meta?: Meta, value: (scope: Scope) => Value | Error};

type NameRef   = {type: 'name_ref',   meta?: Meta, name: string};
type NewStruct = {type: 'new_struct', meta?: Meta, keys: {[key: string]: AST}};
type Call      = {type: 'call',       meta?: Meta, target: AST, method: string, args: Value[]};
type Block     = {type: 'block',      meta?: Meta, code: AST[]};
type NewLambda = {type: 'new_lambda', meta?: Meta, block: Block};

type Error     = {type: 'error', message: string, location?: Location};

type Value     = Unknown | Nothing | Boolean | Number | String | Struct | Lambda;
type Operation = NewStruct | NameRef | Call | Block | NewLambda;

type AST       = Value | Operation;

interface Token {
  type: 'number' | 'string' | 'name' | 'keyword' | 'operator';
  string: string;
  location: Location;
};

export class Lexer {
  lex(code: string): Token[] {
    // TODO
    return [];
  }
}

export class Parser {
  private lexer = new Lexer();

  parse(code: string): Operation[] {
    const tokens = this.lexer.lex(code);

    // TODO
    return [];
  }
}

export class Scope {
  private vars: {[key: string]: Value} = {};

  constructor(
    private parent: Scope | null = null
  ) {}

  newChild() {
    return new Scope(this);
  }

  find(name: string): Value | null {
    return this.vars[name] ||
           this.parent && this.parent.find(name);
  }
}

export class Compiler {
  private parser = new Parser();
  private rootScope = new Scope();

  eval(code: string): Value | Error {
    const expressions = this.parser.parse(code);
    const scope = this.rootScope.newChild();

    let lastExpression: Value | Error = {type: 'nothing'};

    for (const ast of expressions) {
      lastExpression = this.evalAST(ast, scope);

      if (lastExpression.type === 'error') {
        return lastExpression;
      }
    }

    return {type: 'unknown'};
  }

  evalAST(ast: AST, scope: Scope): Value | Error {
    switch (ast.type) {
    case 'block':      return this.evalBlock(ast, scope);
    case 'new_lambda': return this.evalLambda(ast, scope);
    case 'call':       return this.evalCall(ast, scope);
    case 'name_ref':   return this.evalName(ast, scope);
    case 'new_struct': return this.evalStruct(ast, scope);
    default:           return ast;
    }
  }

  private evalBlock(block: Block, scope: Scope): Value | Error {
    const childScope = scope.newChild();
    let lastValue: Value | Error = {type: 'nothing'};

    for (const ast of block.code) {
      lastValue = this.evalAST(ast, childScope);

      if (lastValue.type === 'error') {
        return lastValue;
      }
    }

    return lastValue;
  }

  private evalLambda(ast: NewLambda, scope: Scope): Lambda {
    return {type: 'lambda', value: () => this.evalBlock(ast.block, scope)};
  }

  private evalCall(ast: Call, scope: Scope): Value {
    // TODO
    return {type: 'nothing'};
  }

  private evalName(ast: NameRef, scope: Scope): Value | Error {
    const value = scope.find(ast.name);

    if (!value) {
      return {
        type: 'error',
        message: `Cannot find name ${ast.name}`,
        location: ast.meta && ast.meta.location
      };
    }

    return value;
  }

  private evalStruct(ast: NewStruct, scope: Scope): Struct {
    return {
      type: 'struct',
      value: _(ast.keys).map((value, key) => [key, this.evalAST(value, scope)]).fromPairs().value()
    };
  }
}
