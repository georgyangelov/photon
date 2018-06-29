use std::rc::Rc;
use std::cell::RefCell;

pub mod ast;
pub mod ir;

pub type Shared<T> = Rc<RefCell<T>>;

pub fn make_shared<T>(value: T) -> Shared<T> {
    Rc::new(RefCell::new(value))
}
