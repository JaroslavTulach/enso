//! Definition of the Node component.

#![allow(missing_docs)]
// WARNING! UNDER HEAVY DEVELOPMENT. EXPECT DRASTIC CHANGES.

#[deny(missing_docs)]
pub mod action_bar;
#[warn(missing_docs)]
pub mod error;
pub mod expression;
pub mod input;
pub mod output;
#[warn(missing_docs)]
pub mod profiling;
#[deny(missing_docs)]
pub mod vcs;

pub use error::Error;
pub use expression::Expression;

use crate::prelude::*;

use crate::component::node::profiling::ProfilingLabel;
use crate::component::visualization;
use crate::tooltip;
use crate::view;
use crate::Type;

use enso_frp as frp;
use enso_frp;
use ensogl::animation::delayed::DelayedAnimation;
use ensogl::application::Application;
use ensogl::data::color;
use ensogl::display;
use ensogl::display::shape::*;
use ensogl::display::traits::*;
use ensogl::Animation;
use ensogl_component::shadow;
use ensogl_component::text;
use ensogl_component::text::Text;
use ensogl_hardcoded_theme as theme;
use ensogl_hardcoded_theme;
use std::f32::EPSILON;

use super::edge;
use crate::selection::BoundingBox;



// =================
// === Constants ===
// =================

pub const ACTION_BAR_WIDTH: f32 = 180.0;
pub const ACTION_BAR_HEIGHT: f32 = 15.0;
pub const CORNER_RADIUS: f32 = 14.0;
pub const HEIGHT: f32 = 28.0;
pub const PADDING: f32 = 40.0;
pub const RADIUS: f32 = 14.0;

/// Space between the documentation comment and the node.
pub const COMMENT_MARGIN: f32 = 10.0;

const INFINITE: f32 = 99999.0;
const ERROR_VISUALIZATION_SIZE: (f32, f32) = visualization::container::DEFAULT_SIZE;

const VISUALIZATION_OFFSET_Y: f32 = -120.0;

const ENABLE_VIS_PREVIEW: bool = false;
const VIS_PREVIEW_ONSET_MS: f32 = 4000.0;
const ERROR_PREVIEW_ONSET_MS: f32 = 0000.0;
/// A type of unresolved methods. We filter them out, because we don't want to treat them as types
/// for ports and edges coloring (due to bad UX otherwise).
const UNRESOLVED_SYMBOL_TYPE: &str = "Builtins.Main.Unresolved_Symbol";



// ===============
// === Comment ===
// ===============

/// String with documentation comment text for this node.
///
/// This is just a plain string, as this is what text area expects and node just redirects this
/// value,
pub type Comment = String;



// =============
// === Shape ===
// =============

/// Node background definition.
pub mod background {
    use super::*;

    ensogl::define_shape_system! {
        (style:Style, bg_color:Vector4) {
            let bg_color = Var::<color::Rgba>::from(bg_color);
            let width    = Var::<Pixels>::from("input_size.x");
            let height   = Var::<Pixels>::from("input_size.y");
            let width    = width  - PADDING.px() * 2.0;
            let height   = height - PADDING.px() * 2.0;
            let radius   = RADIUS.px();
            let shape    = Rect((&width,&height)).corners_radius(&radius);
            let shape    = shape.fill(bg_color);
            shape.into()
        }
    }
}

/// Node backdrop. Contains shadow and selection.
pub mod backdrop {
    use super::*;

    ensogl::define_shape_system! {
        (style:Style, selection:f32) {

            let width  = Var::<Pixels>::from("input_size.x");
            let height = Var::<Pixels>::from("input_size.y");
            let width  = width  - PADDING.px() * 2.0;
            let height = height - PADDING.px() * 2.0;

            // === Shadow ===

            let shadow_radius = &height / 2.0;
            let shadow_base   = Rect((&width,&height)).corners_radius(shadow_radius);
            let shadow        = shadow::from_shape(shadow_base.into(),style);


            // === Selection ===

            let sel_color  = style.get_color(ensogl_hardcoded_theme::graph_editor::node::selection);
            let sel_size   = style.get_number(ensogl_hardcoded_theme::graph_editor::node::selection::size);
            let sel_offset = style.get_number(ensogl_hardcoded_theme::graph_editor::node::selection::offset);

            let sel_width   = &width  - 2.px() + &sel_offset.px() * 2.0 * &selection;
            let sel_height  = &height - 2.px() + &sel_offset.px() * 2.0 * &selection;
            let sel_radius  = &sel_height / 2.0;
            let select      = Rect((&sel_width,&sel_height)).corners_radius(&sel_radius);

            let sel2_width  = &width  - 2.px() + &(sel_size + sel_offset).px() * 2.0 * &selection;
            let sel2_height = &height - 2.px() + &(sel_size + sel_offset).px() * 2.0 * &selection;
            let sel2_radius = &sel2_height / 2.0;
            let select2     = Rect((&sel2_width,&sel2_height)).corners_radius(&sel2_radius);

            let select = select2 - select;
            let select = select.fill(sel_color);


             // === Error Pattern  Alternative ===
             // TODO: Remove once the error indicator design is finalised.
             // let repeat      =  Var::<Vector2<Pixels>>::from((10.px(), 10.px()));
             // let error_width =  Var::<Pixels>::from(5.px());
             //
             // let stripe_red   = Rect((error_width, 99999.px()));
             // let pattern = stripe_red.repeat(repeat).rotate(45.0.radians());
             // let mask    = Rect((&width,&height)).corners_radius(&radius);
             // let pattern1 = mask.intersection(pattern).fill(color::Rgba::red());

             // let out =  select + shadow + shape + pattern1;

            // === Final Shape ===

            let out = select + shadow;
            out.into()
        }
    }
}

pub mod drag_area {
    use super::*;

    ensogl::define_shape_system! {
        (style:Style) {
            let width  : Var<Pixels> = "input_size.x".into();
            let height : Var<Pixels> = "input_size.y".into();
            let width  = width  - PADDING.px() * 2.0;
            let height = height - PADDING.px() * 2.0;
            let radius = 14.px();
            let shape  = Rect((&width,&height)).corners_radius(radius);
            let shape  = shape.fill(color::Rgba::new(0.0,0.0,0.0,0.000_001));

            let out = shape;
            out.into()
        }
    }
}



// =======================
// === Error Indicator ===
// =======================

pub mod error_shape {
    use super::*;

    ensogl::define_shape_system! {
        (style:Style,color_rgba:Vector4<f32>) {
            use ensogl_hardcoded_theme::graph_editor::node as node_theme;

            let width  = Var::<Pixels>::from("input_size.x");
            let height = Var::<Pixels>::from("input_size.y");
            let zoom   = Var::<f32>::from("1.0/zoom()");
            let width  = width  - PADDING.px() * 2.0;
            let height = height - PADDING.px() * 2.0;
            let radius = RADIUS.px();

            let error_width         = style.get_number(node_theme::error::width).px();
            let repeat_x            = style.get_number(node_theme::error::repeat_x).px();
            let repeat_y            = style.get_number(node_theme::error::repeat_y).px();
            let stripe_width        = style.get_number(node_theme::error::stripe_width);
            let stripe_angle        = style.get_number(node_theme::error::stripe_angle);
            let repeat              = Var::<Vector2<Pixels>>::from((repeat_x,repeat_y));
            let stripe_width        = Var::<Pixels>::from(zoom * stripe_width);
            let stripe_red          = Rect((&stripe_width,INFINITE.px()));
            let stripe_angle_rad    = stripe_angle.radians();
            let pattern             = stripe_red.repeat(repeat).rotate(stripe_angle_rad);
            let mask                = Rect((&width,&height)).corners_radius(&radius);
            let mask                = mask.grow(error_width);
            let pattern             = mask.intersection(pattern).fill(color_rgba);

            pattern.into()
        }
    }
}



// ==============
// === Crumbs ===
// ==============

#[derive(Clone, Copy, Debug)]
pub enum Endpoint {
    Input,
    Output,
}

#[derive(Clone, Debug)]
pub struct Crumbs {
    pub endpoint: Endpoint,
    pub crumbs:   span_tree::Crumbs,
}

impl Crumbs {
    pub fn input(crumbs: span_tree::Crumbs) -> Self {
        let endpoint = Endpoint::Input;
        Self { endpoint, crumbs }
    }

    pub fn output(crumbs: span_tree::Crumbs) -> Self {
        let endpoint = Endpoint::Output;
        Self { endpoint, crumbs }
    }
}

impl Default for Crumbs {
    fn default() -> Self {
        Self::output(default())
    }
}



// ============
// === Node ===
// ============

ensogl::define_endpoints! {
    Input {
        select                (),
        deselect              (),
        enable_visualization  (),
        disable_visualization (),
        set_visualization     (Option<visualization::Definition>),
        set_disabled          (bool),
        set_input_connected   (span_tree::Crumbs,Option<Type>,bool),
        set_expression        (Expression),
        set_comment           (Comment),
        set_error             (Option<Error>),
        /// Set the expression USAGE type. This is not the definition type, which can be set with
        /// `set_expression` instead. In case the usage type is set to None, ports still may be
        /// colored if the definition type was present.
        set_expression_usage_type         (Crumbs,Option<Type>),
        set_output_expression_visibility  (bool),
        set_vcs_status                    (Option<vcs::Status>),
        /// Indicate whether preview visualisations should be delayed or immediate.
        quick_preview_vis                 (bool),
        set_view_mode                     (view::Mode),
        set_profiling_min_global_duration (f32),
        set_profiling_max_global_duration (f32),
        set_profiling_status              (profiling::Status),
        /// Indicate whether on hover the quick action icons should appear.
        show_quick_action_bar_on_hover    (bool)
    }
    Output {
        /// Press event. Emitted when user clicks on non-active part of the node, like its
        /// background. In edit mode, the whole node area is considered non-active.
        background_press         (),
        expression               (Text),
        comment                  (Comment),
        skip                     (bool),
        freeze                   (bool),
        hover                    (bool),
        error                    (Option<Error>),
        /// Whether visualization was permanently enabled (e.g. by pressing the button).
        visualization_enabled    (bool),
        /// Visualization can be visible even when it is not enabled, e.g. when showing preview.
        visualization_visible    (bool),
        visualization_path       (Option<visualization::Path>),
        expression_label_visible (bool),
        tooltip                  (tooltip::Style),
        bounding_box             (BoundingBox)
    }
}

/// The visual node representation.
///
/// ## Origin
/// Please note that the origin of the node is on its left side, centered vertically. This decision
/// was made to both optimise performance and make the origin point more meaningful. When editing
/// the node, its width changes, while its left border remains still. When expanding the node, its
/// height changes, while its top remains at the same place. Thus, while editing or expanding the
/// node, there is no need to update its position. Moreover, the chosen origin point is more natural
/// than origin placed in other possible places, including the upper-left corner of its bounding
/// box. The `x` symbolises the origin on the following drawing:
///
/// ```text
///   ╭─────────────────╮
///  x│                 │
///   ╰─────────────────╯
/// ```
///
/// ## FRP Event Architecture.
/// Nodes FRP architecture is designed for efficiency. Event with millions nodes on the stage, only
/// small amount of events will be passed around on user action. This is not always simple, and it
/// required a careful, well thought architecture.
///
/// Take for example the `edit_mode` event. It is emitted when user presses the `cmd` button. The
/// following requirements should be hold:
///
/// 1. If the mouse is not over a node, nothing happens.
/// 2. If the mouse traverses over the node with `cmd` being hold, the mouse cursor should change to
///    text cursor to indicate that editing of the expression is possible.
/// 3. If the mouse was over the node when pressing `cmd`, the mouse cursor should change to text
///    cursor as well.
///
/// The points 1 and 2 are pretty easy to be done. We can discover mouse hover from inside of the
/// node and react in the right way. The point 3 is tricky. There are several possible solutions
/// out there:
///
/// A. After pressing / releasing `cmd` we should send an event to every node on the stage to
///    indicate that the "edit mode" is on. This is a simple solution, but also very inefficient
///    with a lot of nodes on the stage.
///
/// B. We could pass a special FRP output to node constructor, like
///    `is_edit_mode_on:frp::Sampler<bool>`, which could be sampled by the node whenever the mouse
///    hovers it. This will solve the requirement 2, but will not work with requirement 3.
///
/// C. We could discover inside of node when mouse hovers it (either the drag area, or ports, or
///    anything else that we consider part of the node), and emit it as an output event. Then we
///    can capture the event in the graph editor and tag it with the node id. Having the information
///    in place, we can send events to the currently hovered node whenever we need, directly from
///    the graph editor. This solves all issues in a very efficient and elegant way, but is somehow
///    complex logically (the events are emitted from node to graph, then processed there and
///    emitted back to the right node).
///
/// Currently, the solution "C" (most optimal) is implemented here.
#[derive(Clone, CloneRef, Debug)]
#[allow(missing_docs)]
pub struct Node {
    pub model: Rc<NodeModel>,
    pub frp:   Frp,
}

impl AsRef<Node> for Node {
    fn as_ref(&self) -> &Self {
        self
    }
}

impl Deref for Node {
    type Target = Frp;
    fn deref(&self) -> &Self::Target {
        &self.frp
    }
}

/// Internal data of `Node`
#[derive(Clone, CloneRef, Debug)]
#[allow(missing_docs)]
pub struct NodeModel {
    pub app:                 Application,
    pub display_object:      display::object::Instance,
    pub logger:              Logger,
    pub backdrop:            backdrop::View,
    pub background:          background::View,
    pub drag_area:           drag_area::View,
    pub error_indicator:     error_shape::View,
    pub profiling_label:     ProfilingLabel,
    pub input:               input::Area,
    pub output:              output::Area,
    pub visualization:       visualization::Container,
    pub error_visualization: error::Container,
    pub action_bar:          action_bar::ActionBar,
    pub vcs_indicator:       vcs::StatusIndicator,
    pub style:               StyleWatchFrp,
    pub comment:             text::Area,
}

impl NodeModel {
    /// Constructor.
    pub fn new(app: &Application, registry: visualization::Registry) -> Self {
        ensogl::shapes_order_dependencies! {
            app.display.scene() => {
                //TODO[ao] The two lines below should not be needed - the ordering should be
                //    transitive. But removing them causes a visual glitches described in
                //    https://github.com/enso-org/ide/issues/1624
                //    The matter should be further investigated.
                edge::back::corner        -> backdrop;
                edge::back::line          -> backdrop;
                edge::back::corner        -> error_shape;
                edge::back::line          -> error_shape;
                error_shape               -> backdrop;
                backdrop                  -> output::port::single_port;
                backdrop                  -> output::port::multi_port;
                output::port::single_port -> background;
                output::port::multi_port  -> background;
                background                -> drag_area;
                drag_area                 -> edge::front::corner;
                drag_area                 -> edge::front::line;
                edge::front::corner       -> input::port::hover;
                edge::front::line         -> input::port::hover;
                input::port::hover        -> input::port::viz;
            }
        }

        let scene = app.display.scene();
        let logger = Logger::new("node");

        let main_logger = Logger::new_sub(&logger, "main_area");
        let drag_logger = Logger::new_sub(&logger, "drag_area");
        let error_indicator_logger = Logger::new_sub(&logger, "error_indicator");

        let error_indicator = error_shape::View::new(&error_indicator_logger);
        let profiling_label = ProfilingLabel::new(app);
        let backdrop = backdrop::View::new(&main_logger);
        let background = background::View::new(&main_logger);
        let drag_area = drag_area::View::new(&drag_logger);
        let vcs_indicator = vcs::StatusIndicator::new(app);
        let display_object = display::object::Instance::new(&logger);

        display_object.add_child(&profiling_label);
        display_object.add_child(&drag_area);
        display_object.add_child(&backdrop);
        display_object.add_child(&background);
        display_object.add_child(&vcs_indicator);

        // Disable shadows to allow interaction with the output port.
        let shape_system = scene
            .layers
            .main
            .shape_system_registry
            .shape_system(scene, PhantomData::<backdrop::DynamicShape>);
        shape_system.shape_system.set_pointer_events(false);

        let input = input::Area::new(&logger, app);
        let visualization = visualization::Container::new(&logger, app, registry);

        display_object.add_child(&visualization);
        display_object.add_child(&input);

        let error_visualization = error::Container::new(scene);
        let (x, y) = ERROR_VISUALIZATION_SIZE;
        error_visualization.set_size.emit(Vector2(x, y));

        let action_bar = action_bar::ActionBar::new(&logger, app);
        display_object.add_child(&action_bar);
        scene.layers.above_nodes.add_exclusive(&action_bar);

        let output = output::Area::new(&logger, app);
        display_object.add_child(&output);

        let style = StyleWatchFrp::new(&app.display.scene().style_sheet);

        let comment = text::Area::new(app);
        display_object.add_child(&comment);

        let app = app.clone_ref();
        Self {
            app,
            display_object,
            logger,
            backdrop,
            background,
            drag_area,
            error_indicator,
            profiling_label,
            input,
            output,
            visualization,
            error_visualization,
            action_bar,
            vcs_indicator,
            style,
            comment,
        }
        .init()
    }

    pub fn get_crumbs_by_id(&self, id: ast::Id) -> Option<Crumbs> {
        let input_crumbs = self.input.get_crumbs_by_id(id).map(Crumbs::input);
        input_crumbs.or_else(|| self.output.get_crumbs_by_id(id).map(Crumbs::output))
    }

    fn init(self) -> Self {
        self.set_expression(Expression::new_plain("empty"));
        self
    }

    pub fn width(&self) -> f32 {
        self.input.width.value()
    }

    pub fn height(&self) -> f32 {
        HEIGHT
    }

    fn set_expression(&self, expr: impl Into<Expression>) {
        let expr = expr.into();
        self.output.set_expression(&expr);
        self.input.set_expression(&expr);
    }

    fn set_expression_usage_type(&self, crumbs: &Crumbs, tp: &Option<Type>) {
        match crumbs.endpoint {
            Endpoint::Input => self.input.set_expression_usage_type(&crumbs.crumbs, tp),
            Endpoint::Output => self.output.set_expression_usage_type(&crumbs.crumbs, tp),
        }
    }

    fn set_width(&self, width: f32) -> Vector2 {
        let height = self.height();
        let size = Vector2(width, height);
        let padded_size = size + Vector2(PADDING, PADDING) * 2.0;
        self.backdrop.size.set(padded_size);
        self.background.size.set(padded_size);
        self.drag_area.size.set(padded_size);
        self.error_indicator.size.set(padded_size);
        self.vcs_indicator.set_size(padded_size);
        self.backdrop.mod_position(|t| t.x = width / 2.0);
        self.background.mod_position(|t| t.x = width / 2.0);
        self.drag_area.mod_position(|t| t.x = width / 2.0);
        self.error_indicator.set_position_x(width / 2.0);
        self.vcs_indicator.set_position_x(width / 2.0);

        let action_bar_width = ACTION_BAR_WIDTH;
        self.action_bar.mod_position(|t| {
            t.x = width + CORNER_RADIUS + action_bar_width / 2.0;
        });
        self.action_bar.frp.set_size(Vector2::new(action_bar_width, ACTION_BAR_HEIGHT));

        let visualization_pos = NodeModel::position_of_visualization_relative_to_position_of_node_of_given_size(size);
        self.error_visualization.set_position_xy(visualization_pos);
        self.visualization.set_position_xy(visualization_pos);

        size
    }

    fn position_of_visualization_relative_to_position_of_node_of_given_size(node_size: Vector2) -> Vector2 {
        Vector2(node_size.x / 2.0, VISUALIZATION_OFFSET_Y)
    }

    pub fn visualization(&self) -> &visualization::Container {
        &self.visualization
    }

    fn set_error(&self, error: Option<&Error>) {
        if let Some(error) = error {
            self.error_visualization.display_kind(*error.kind);
            if let Some(error_data) = error.visualization_data() {
                self.error_visualization.set_data(&error_data);
            }
            self.display_object.add_child(&self.error_visualization);
        } else {
            self.error_visualization.unset_parent();
        }
    }

    fn set_error_color(&self, color: &color::Lcha) {
        self.error_indicator.color_rgba.set(color::Rgba::from(color).into());
        if color.alpha < EPSILON {
            self.error_indicator.unset_parent();
        } else {
            self.display_object.add_child(&self.error_indicator);
        }
    }
}

impl Node {
    pub fn new(app: &Application, registry: visualization::Registry) -> Self {
        let frp = Frp::new();
        let network = &frp.network;
        let out = &frp.output;
        let model = Rc::new(NodeModel::new(app, registry));
        let selection = Animation::<f32>::new(network);

        // TODO[ao] The comment color should be animated, but this is currently slow. Will be fixed
        //      in https://github.com/enso-org/ide/issues/1031
        // let comment_color    = color::Animation::new(network);
        let error_color_anim = color::Animation::new(network);
        let style = StyleWatch::new(&app.display.scene().style_sheet);
        let style_frp = &model.style;
        let action_bar = &model.action_bar.frp;
        // Hook up the display object position updates to the node's FRP. Required to calculate the
        // bounding box.
        frp::extend! { network
            position <- source::<Vector2>();
        }
        model.display_object.set_on_updated(f!((p) position.emit(p.position().xy())));

        frp::extend! { network

            // === Hover ===
            // The hover discovery of a node is an interesting process. First, we discover whether
            // ths user hovers the drag area. The input port manager merges this information with
            // port hover events and outputs the final hover event for any part inside of the node.

            let drag_area           = &model.drag_area.events;
            drag_area_hover        <- bool(&drag_area.mouse_out,&drag_area.mouse_over);
            model.input.set_hover  <+ drag_area_hover;
            model.output.set_hover <+ model.input.body_hover;
            out.source.hover       <+ model.output.body_hover;


            // === Background Press ===

            out.source.background_press <+ model.drag_area.events.mouse_down;
            out.source.background_press <+ model.input.on_background_press;


            // === Selection ===

            deselect_target  <- frp.deselect.constant(0.0);
            select_target    <- frp.select.constant(1.0);
            selection.target <+ any(&deselect_target,&select_target);
            eval selection.value ((t) model.backdrop.selection.set(*t));


            // === Expression ===

            let unresolved_symbol_type = Some(Type(ImString::new(UNRESOLVED_SYMBOL_TYPE)));
            filtered_usage_type <- frp.set_expression_usage_type.filter(
                move |(_,tp)| *tp != unresolved_symbol_type
            );
            eval filtered_usage_type (((a,b)) model.set_expression_usage_type(a,b));
            eval frp.set_expression  ((a)     model.set_expression(a));
            out.source.expression                  <+ model.input.frp.expression;
            model.input.set_connected              <+ frp.set_input_connected;
            model.input.set_disabled               <+ frp.set_disabled;
            model.output.set_expression_visibility <+ frp.set_output_expression_visibility;


            // === Comment ===

            let comment_base_color = style_frp.get_color(theme::graph_editor::node::text);
            // comment_color.target <+ all_with(
            comment_color <- all_with(
                &comment_base_color, &model.output.expression_label_visibility,
                |&base_color,&expression_visible| {
                    let mut color = color::Lcha::from(base_color);
                    color.mod_alpha(|alpha| {
                        // Comment is hidden when output expression (i.e. node name) is visible.
                        if expression_visible { *alpha = 0.0 }
                    });
                    color
            });
            eval comment_color ((value) model.comment.set_color_all(color::Rgba::from(value)));

            eval model.comment.width ([model](width)
                model.comment.set_position_x(-*width - COMMENT_MARGIN));
            eval model.comment.height ([model](height)
                model.comment.set_position_y(*height / 2.0));
            model.comment.set_content <+ frp.set_comment;
            out.source.comment        <+ model.comment.content.map(|text| text.to_string());


            // === Size ===

            new_size <- model.input.frp.width.map(f!((w) model.set_width(*w)));
            eval new_size ((t) model.output.frp.set_size.emit(t));


            // === Action Bar ===

            let visualization_enabled = action_bar.action_visibility.clone_ref();
            out.source.skip   <+ action_bar.action_skip;
            out.source.freeze <+ action_bar.action_freeze;
            show_action_bar   <- out.hover  && frp.show_quick_action_bar_on_hover;
            eval show_action_bar ((t) action_bar.set_visibility(t));
            eval frp.show_quick_action_bar_on_hover((value) action_bar.show_on_hover(value));


            // === View Mode ===

            model.input.set_view_mode           <+ frp.set_view_mode;
            model.output.set_view_mode          <+ frp.set_view_mode;
            model.profiling_label.set_view_mode <+ frp.set_view_mode;
            model.vcs_indicator.set_visibility  <+ frp.set_view_mode.map(|&mode| {
                !matches!(mode,view::Mode::Profiling {..})
            });
        }


        // === Visualizations & Errors ===

        let hover_onset_delay = DelayedAnimation::new(network);
        hover_onset_delay.set_delay(VIS_PREVIEW_ONSET_MS);
        hover_onset_delay.set_duration(0.0);

        frp::extend! { network

            frp.source.error <+ frp.set_error;
            is_error_set <- frp.error.map(|err| err.is_some());
            no_error_set <- not(&is_error_set);
            error_color_anim.target <+ all_with(&frp.error,&frp.set_view_mode,
                f!([style](error,&mode)
                    let error_color = Self::error_color(error,&style);
                    match mode {
                        view::Mode::Normal    => error_color,
                        view::Mode::Profiling => error_color.to_grayscale(),
                    }
                ));

            eval frp.set_visualization ((t) model.visualization.frp.set_visualization.emit(t));
            visualization_enabled_frp <- bool(&frp.disable_visualization,&frp.enable_visualization);
            eval visualization_enabled_frp ((enabled)
                model.action_bar.set_action_visibility_state(enabled)
            );

            // Show preview visualisation after some delay, depending on whether we show an error
            // or are in quick preview mode. Also, omit the preview if we don't have an
            // expression.
            has_tooltip    <- model.output.frp.tooltip.map(|tt| tt.has_content());
            has_expression <- frp.set_expression.map(|expr| *expr != Expression::default());

            preview_show_delay <- all(&frp.quick_preview_vis,&is_error_set);
            preview_show_delay <- preview_show_delay.map(|(quick_preview,is_error)| {
                match(is_error,quick_preview) {
                    (true,_)      => ERROR_PREVIEW_ONSET_MS,
                    (false,false) => if ENABLE_VIS_PREVIEW {VIS_PREVIEW_ONSET_MS} else {f32::MAX},
                    (false,true)  => 0.0
                }
            });
            hover_onset_delay.set_delay <+ preview_show_delay;
            hide_tooltip                <- preview_show_delay.map(|&delay| delay <= EPSILON);

            outout_hover            <- model.output.on_port_hover.map(|s| s.is_on());
            hover_onset_delay.start <+ outout_hover.on_true();
            hover_onset_delay.reset <+ outout_hover.on_false();
            preview_visible         <- bool(&hover_onset_delay.on_reset,&hover_onset_delay.on_end);
            preview_visible         <- preview_visible && has_expression;
            preview_visible         <- preview_visible.on_change();

            visualization_visible            <- visualization_enabled || preview_visible;
            visualization_visible            <- visualization_visible && no_error_set;
            visualization_visible_on_change  <- visualization_visible.on_change();
            frp.source.visualization_visible <+ visualization_visible_on_change;
            frp.source.visualization_enabled <+ visualization_enabled;
            eval visualization_visible_on_change ((is_visible)
                model.visualization.frp.set_visibility(is_visible)
            );
            init <- source::<()>();
            frp.source.visualization_path <+ model.visualization.frp.visualisation.all_with(&init,|def_opt,_| {
                def_opt.as_ref().map(|def| def.signature.path.clone_ref())
            });

            // Ensure the preview is visible above all other elements, but the normal visualisation
            // is below nodes.
            layer_on_hover     <- preview_visible.on_false().map(|_| visualization::Layer::Default);
            layer_on_not_hover <- preview_visible.on_true().map(|_| visualization::Layer::Front);
            layer              <- any(layer_on_hover,layer_on_not_hover);
            model.visualization.frp.set_layer <+ layer;

            update_error <- all(frp.set_error,preview_visible);
            eval update_error([model]((error,visible)){
                if *visible {
                     model.set_error(error.as_ref());
                } else {
                     model.set_error(None);
                }
            });

            eval error_color_anim.value ((value) model.set_error_color(value));

        }

        // === Profiling Indicator ===

        frp::extend! { network
            model.profiling_label.set_min_global_duration
                <+ frp.set_profiling_min_global_duration;
            model.profiling_label.set_max_global_duration
                <+ frp.set_profiling_max_global_duration;
            model.profiling_label.set_status <+ frp.set_profiling_status;
            model.input.set_profiling_status <+ frp.set_profiling_status;
        }

        let bg_color_anim = color::Animation::new(network);

        frp::extend! { network

            // === Color Handling ===

            let bgg = style_frp.get_color(ensogl_hardcoded_theme::graph_editor::node::background);
            let profiling_theme = profiling::Theme::from_styles(style_frp,network);

            profiling_color <- all_with5
                (&frp.set_profiling_status,&frp.set_profiling_min_global_duration,
                &frp.set_profiling_max_global_duration,&profiling_theme,&bgg,
                |&status,&min,&max,&theme,&bgg| {
                    if status.is_finished() {
                        status.display_color(min,max,theme).with_alpha(1.0)
                    } else {
                        color::Lcha::from(bgg)
                    }
                });

            bg_color_anim.target <+ all_with3(&bgg,&frp.set_view_mode,&profiling_color,
                |bgg,&mode,&profiling_color| {
                    match mode {
                        view::Mode::Normal    => color::Lcha::from(*bgg),
                        view::Mode::Profiling => profiling_color,
                    }
                });

            // FIXME [WD]: Uncomment when implementing disabled icon.
            // bg_color <- frp.set_disabled.map(f!([model,style](disabled) {
            //     model.input.frp.set_disabled(*disabled);
            //     let bg_color_path = ensogl_hardcoded_theme::graph_editor::node::background;
            //     if *disabled { style.get_color_dim(bg_color_path) }
            //     else         { style.get_color(bg_color_path) }
            // }));
            // bg_color_anim.target <+ bg_color;
            // eval bg_color_anim.value ((c)
            //     model.background.bg_color.set(color::Rgba::from(c).into())
            // );

            eval bg_color_anim.value ((c)
                model.background.bg_color.set(color::Rgba::from(c).into()));


            // === Tooltip ===

            // Hide tooltip if we show the preview vis.
            frp.source.tooltip <+ preview_visible.on_true().constant(tooltip::Style::unset_label());
            // Propagate output tooltip. Only if it is not hidden, or to disable it.
            block_tooltip      <- hide_tooltip && has_tooltip;
            frp.source.tooltip <+ model.output.frp.tooltip.gate_not(&block_tooltip);


            // === Type Labels ===

            model.output.set_type_label_visibility
                <+ visualization_visible.not().and(&no_error_set);


            // === Bounding Box ===

            let visualization_size             = &model.visualization.frp.size;
            visualization_enabled_and_visible <- visualization_enabled && visualization_visible;
            bounding_box_input <- all4(&new_size,&position,&visualization_enabled_and_visible,visualization_size);
            out.source.bounding_box <+ bounding_box_input.map(
                |(node_size,node_position,visualization_enabled_and_visible,visualization_size)| {
                    let bounding_box_position = node_position - Vector2::new(0.0, node_size.y / 2.0);
                    let mut bb = BoundingBox::from_position_and_size(bounding_box_position, *node_size);
                    if *visualization_enabled_and_visible {
                        let pos_of_visualization_relative_to_pos_of_node = NodeModel::position_of_visualization_relative_to_position_of_node_of_given_size(*node_size);
                        let absolute_pos_of_visualization = node_position + pos_of_visualization_relative_to_pos_of_node;
                        let absolute_pos_of_visualization_bounding_box = absolute_pos_of_visualization - visualization_size / 2.0;
                        let visualization_bounding_box = BoundingBox::from_position_and_size(absolute_pos_of_visualization_bounding_box, *visualization_size);
                        DEBUG!("MCDBG: vis enabled, v.size=" visualization_size;? " n.pos=" node_position;? " n.size=" node_size;? " v.bbox=" visualization_bounding_box;?);
                        bb.grow_to_include(&visualization_bounding_box);
                    }
                    bb
                });


            // === VCS Handling ===

            model.vcs_indicator.frp.set_status <+ frp.set_vcs_status;
        }

        // Init defaults.
        init.emit(());
        model.error_visualization.set_layer(visualization::Layer::Front);
        frp.set_error.emit(None);
        frp.set_disabled.emit(false);
        frp.show_quick_action_bar_on_hover.emit(true);

        Self { model, frp }
    }

    fn error_color(error: &Option<Error>, style: &StyleWatch) -> color::Lcha {
        use ensogl_hardcoded_theme::graph_editor::node::error as error_theme;

        if let Some(error) = error {
            let path = match *error.kind {
                error::Kind::Panic => error_theme::panic,
                error::Kind::Dataflow => error_theme::dataflow,
            };
            style.get_color(path).into()
        } else {
            color::Lcha::transparent()
        }
    }
}

impl display::Object for Node {
    fn display_object(&self) -> &display::object::Instance {
        &self.model.display_object
    }
}
