const moduleAlias = require('module-alias');

moduleAlias.addAlias('~', __dirname);
moduleAlias.addAlias('@photon', `${__dirname}/photon`);

import { Compiler } from '@photon/compiler';

const compiler = new Compiler();
console.log(`Compiling... ${compiler.compile()}`);
