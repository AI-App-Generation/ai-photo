package com.bblvn.composebaseproject.corefeatures.generation

import com.bblvn.composebaseproject.data.domain.ToolOption
import com.bblvn.composebaseproject.data.domain.ToolType

/**
 * Fixed English prompts for the 5 quick tools.
 *
 * THESE MUST NEVER MOVE INTO `strings.xml` (R6.4). A translated prompt is a broken prompt: when
 * the user switches the app to Vietnamese, a localized resource would send Vietnamese instructions
 * to Seedream and wreck the output. They live in code, in English, permanently.
 *
 * They are written long and explicit, with negative constraints, in the same spirit as the catalog
 * prompts — prompt length and specificity are what keep Seedream on task.
 */
object ToolPromptProvider {

    /** Shared tail appended to every tool prompt. */
    private const val COMMON_NEGATIVES =
        "Strict negatives: no text, no captions, no watermark, no logo, no signature, no frame or " +
            "border, no collage or split-screen, no duplicated subject, no extra limbs or fingers, " +
            "no distorted or asymmetric facial features, no plastic or waxy skin, no over-smoothed " +
            "skin that erases pores and texture, no cartoon, anime, illustration or 3D-render look, " +
            "no heavy HDR halos, no oversaturated neon colour cast, no visible compression " +
            "artefacts, no change to the person's age, gender, ethnicity, body weight or bone " +
            "structure. Output exactly one photorealistic image."

    private const val IDENTITY_LOCK =
        "Treat the uploaded photograph as the ONLY reference for the person. Preserve facial " +
            "identity with maximum accuracy: face shape, jawline, cheekbones, eye shape and " +
            "spacing, iris colour, eyebrow shape, nose, lips, teeth, skin tone and undertone, " +
            "moles, freckles, scars, facial hair and hairline must all remain exactly as they are " +
            "in the source. The result must be instantly recognisable as the same individual."

    private const val ENHANCE = """
High-fidelity photo restoration and upscale of the uploaded image. Reconstruct genuine optical
detail rather than inventing new content: recover fine texture in skin pores, individual hair
strands, eyelashes, eyebrows, iris patterns and catchlights, fabric weave, and background
micro-detail. Remove sensor noise, chroma noise, colour banding, JPEG blocking and haze. Correct
motion blur and mild focus softness so edges resolve crisply without ringing or halo outlines.
Rebuild a natural tonal curve with clean neutral whites, deep but detailed shadows, and highlights
that are not clipped. Keep the original composition, crop, aspect ratio, pose, expression, clothing,
background content and lighting direction exactly as they are — this is a restoration, not a
reinterpretation. The result should look like the same photograph taken on a modern full-frame
camera with a sharp prime lens.
"""

    private const val ENHANCE_STRONG_SUFFIX =
        "Apply a stronger restoration pass: push micro-contrast and local clarity noticeably " +
            "further on the eyes, eyelashes, eyebrows, lips and individual hair strands; make skin " +
            "texture crisp and clearly defined while keeping it anatomically real; deepen detail " +
            "recovery in dark areas and sharpen fabric and background texture. Stay short of an " +
            "obviously over-sharpened, crunchy or HDR look."

    private const val ENHANCE_NATURAL_SUFFIX =
        "Keep the restoration restrained and believable: the viewer should think the photo was " +
            "always this good, never that it was processed. Prefer slightly soft and natural over " +
            "aggressively sharp."

    private const val REMOVE_BG = """
Isolate the main subject of the uploaded image and replace everything behind it with a completely
flat, uniform, seamless background. Cut the subject with matting-grade precision: keep every
individual strand of flyaway hair, fur, eyelashes, thin jewellery, glasses frames and arms, and any
semi-transparent fabric edge. Preserve soft natural edge transparency instead of producing a hard
cardboard cut-out outline. Do not leave any coloured fringe, halo or residue from the original
background along the subject's edge. Keep the subject itself completely unchanged: same pose, same
proportions, same clothing, same lighting on the body, same colours, same crop and aspect ratio.
Do not add a new shadow, reflection, gradient, vignette, texture, pattern, prop or scenery of any
kind — the background must be one single solid colour edge to edge.
"""

    private const val BG_TRANSPARENT_SUFFIX =
        "Render the background as a perfectly uniform pure mid-grey (#808080) field with absolutely " +
            "no gradient, noise or texture, so it can be keyed out cleanly to transparency " +
            "afterwards. Do not let any grey spill onto the subject."

    private const val BG_WHITE_SUFFIX =
        "Render the background as a perfectly uniform pure white (#FFFFFF) studio field with no " +
            "gradient, vignette or shadow falloff, like a high-key e-commerce product shot. Keep " +
            "the subject's edges clearly separated from the white so light-coloured hair and " +
            "clothing do not disappear."

    private const val BG_BLACK_SUFFIX =
        "Render the background as a perfectly uniform pure black (#000000) field with no gradient " +
            "or glow. Keep the subject's edges clearly separated from the black so dark hair and " +
            "clothing do not disappear into it."

    private const val COLORIZE = """
Colourise this black-and-white or faded photograph into a convincing full-colour photograph.
Infer plausible real-world colour from context, material and era: realistic skin tones with correct
undertones and natural variation across the face, believable hair colour, eye colour consistent
with the visible tonal value, period-appropriate clothing dyes and fabric colours, and natural
colours for sky, foliage, wood, metal, stone and skin-adjacent surfaces. Keep every existing edge,
contour, shadow, highlight, grain structure and detail exactly where it is — colour is being added
to the existing image, nothing is being redrawn, moved, sharpened away or replaced. Do not alter
the person's identity, age, expression, pose, clothing design or the background content. Avoid the
classic bad-colourisation failures: flat single-hue skin, orange or magenta skin cast, grey lips,
colour bleeding across edges, and patchy areas left desaturated.
"""

    private const val COLOR_NATURAL_SUFFIX =
        "Use a neutral, true-to-life colour balance as if shot on daylight-balanced colour negative " +
            "film: accurate whites, moderate saturation, no stylistic colour grade."

    private const val COLOR_WARM_SUFFIX =
        "Grade towards a warm golden-hour palette: gently warm highlights, amber-leaning skin " +
            "tones, soft warm ambient light. Keep whites from turning orange and keep shadows " +
            "neutral rather than muddy."

    private const val COLOR_CINEMATIC_SUFFIX =
        "Grade like a modern feature film: slightly cool teal shadows against warm skin tones, " +
            "gentle highlight rolloff, mild filmic contrast curve and subtle grain. Keep skin tones " +
            "natural and never let the grade turn the image green or plastic."

    private const val FACE_SWAP = """
You are given exactly two images. IMAGE 1 is the FACE SOURCE: use it as the only reference for the
person's facial identity. IMAGE 2 is the TARGET SCENE: everything else comes from it.

Replace the face of the person in IMAGE 2 with the face from IMAGE 1. Transfer the complete facial
identity — face shape, jawline, cheekbones, brow ridge, eye shape and spacing, iris colour,
eyebrows, nose, mouth and lip shape, teeth, chin, skin tone, undertone, moles, freckles and facial
hair. The result must be immediately recognisable as the person from IMAGE 1.

Everything else in IMAGE 2 must remain untouched and pixel-faithful: head pose and angle, gaze
direction, facial expression, hairstyle and hair, body, hands, clothing, accessories, background,
composition, crop and aspect ratio. Match the swapped face to the scene physically — same lighting
direction, same key and fill intensity, same colour temperature, same shadow softness, same depth
of field and focus falloff, same film grain and noise level, and the same skin rendering as the
neck and ears it connects to. Blend the jawline, hairline, ears and neck seamlessly so no edge,
mask boundary, colour step or texture discontinuity is visible anywhere.
"""

    private const val VIRTUAL_TRY_ON = """
You are given exactly two images. IMAGE 1 is the PERSON. IMAGE 2 is the GARMENT to be worn.

Dress the person from IMAGE 1 in the garment from IMAGE 2. Reproduce the garment faithfully: its
exact colour, print, pattern placement, logo, texture, fabric weight and drape, neckline, collar,
sleeve length, hem length, closure, buttons, zips, seams and trim must all match IMAGE 2. Fit the
garment to this specific body — follow the shoulders, chest, waist and hips, wrap correctly around
the torso and arms, and produce natural folds, creases, tension lines and drape consistent with the
person's pose and the fabric's weight.

Keep everything about the person unchanged: face and full facial identity, hairstyle, skin tone,
body shape and proportions, height, pose, hand positions, visible jewellery, background,
composition, crop and aspect ratio. Only the clothing being replaced changes. Light the garment
with the same direction, intensity, colour temperature and shadow softness as the rest of the
photograph, and cast correct contact shadows where the garment meets the body. Do not float,
stretch, mirror or distort the garment, do not crop out limbs, and do not leave any part of the
previous clothing visible at the collar, cuffs or hem.
"""

    /**
     * The final prompt for a tool run. [option] may be null for tools without options; an option
     * belonging to a different tool is ignored rather than mixed in.
     */
    fun promptFor(tool: ToolType, option: ToolOption? = null): String {
        val chosen = option?.takeIf { it in tool.options } ?: tool.defaultOption
        val body = when (tool) {
            ToolType.ENHANCE -> join(IDENTITY_LOCK, ENHANCE, suffixFor(chosen))
            ToolType.REMOVE_BG -> join(REMOVE_BG, suffixFor(chosen))
            ToolType.COLORIZE -> join(IDENTITY_LOCK, COLORIZE, suffixFor(chosen))
            ToolType.FACE_SWAP -> join(FACE_SWAP)
            ToolType.VIRTUAL_TRY_ON -> join(VIRTUAL_TRY_ON)
        }
        return join(body, COMMON_NEGATIVES)
    }

    private fun suffixFor(option: ToolOption?): String = when (option) {
        ToolOption.ENHANCE_NATURAL -> ENHANCE_NATURAL_SUFFIX
        ToolOption.ENHANCE_STRONG -> ENHANCE_STRONG_SUFFIX
        ToolOption.BG_TRANSPARENT -> BG_TRANSPARENT_SUFFIX
        ToolOption.BG_WHITE -> BG_WHITE_SUFFIX
        ToolOption.BG_BLACK -> BG_BLACK_SUFFIX
        ToolOption.COLOR_NATURAL -> COLOR_NATURAL_SUFFIX
        ToolOption.COLOR_WARM -> COLOR_WARM_SUFFIX
        ToolOption.COLOR_CINEMATIC -> COLOR_CINEMATIC_SUFFIX
        null -> ""
    }

    private fun join(vararg parts: String): String = parts
        .map { it.trimIndent().replace('\n', ' ').replace(Regex(" +"), " ").trim() }
        .filter { it.isNotEmpty() }
        .joinToString(" ")
}
