# ADD Step 3: Choose System Elements to Refine

## Iteration 1

## Selected element to refine
Because this is greenfield development, the element selected for refinement is the Hotel Pricing System as a whole, consistent with ADD 3.0 guidance for an initial iteration.

## Reason for selecting this element
Refining the full system is necessary to establish:
- the external context required by HPS-1, HPS-2, HPS-3, and CON-2;
- the initial internal decomposition required by CRN-1;
- boundaries that can later support QA-1, QA-2, QA-3, QA-4, and QA-6;
- a team-friendly division of responsibilities supporting CRN-3.

## External elements relevant to the refinement
The system-level refinement must account for these external elements named in the inputs:
- Business user and administrator using web browsers (HPS-1 to HPS-6, CON-1).
- Cloud provider identity service for credential validation and user management context (HPS-1, QA-5, CON-2).
- Channel management system that must receive price changes (HPS-2, QA-2).
- External systems that query prices through the query API (HPS-3).

## Internal elements expected from the refinement
At this step, the intent is to derive coarse-grained internal elements rather than detailed subcomponents. The refinement should identify at least:
- a browser-based frontend element;
- backend elements for pricing-related capabilities and administrative capabilities as needed;
- explicit external integration boundaries;
- a boundary supporting current REST access and future alternative query protocols (QA-6, CON-5).

## Deferred refinement targets
Detailed decomposition of any selected backend element is deferred to later iterations, especially for:
- mechanisms focused on reliability and availability;
- deployment infrastructure details;
- testing and observability details.

## Self-Reflection (Step 3)
- Whether only prior knowledge was used: yes — the chosen element and rationale come directly from ADD 3.0 guidance and the provided case inputs.
- Whether current iteration drivers are addressed: yes — refining the whole system is the appropriate starting point for establishing overall structure.
- Whether diagrams use correct Mermaid/PlantUML format: yes — no diagrams were required in this step.
- Whether any undeclared assumptions were made: none
