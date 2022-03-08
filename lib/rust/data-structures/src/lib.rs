//! Library of general data structures.

// === Features ===
#![feature(associated_type_bounds)]
#![feature(test)]
#![feature(trait_alias)]
// === Non-Standard Linter Configuration ===
#![deny(unconditional_recursion)]
#![warn(missing_copy_implementations)]
#![warn(missing_debug_implementations)]
#![warn(missing_docs)]
#![warn(trivial_casts)]
#![warn(trivial_numeric_casts)]
#![warn(unsafe_code)]
#![warn(unused_import_braces)]


// ==============
// === Export ===
// ==============

pub mod dependency_graph;
pub mod diet;
pub mod hash_map_tree;
pub mod index;
pub mod opt_vec;

pub use enso_prelude as prelude;
